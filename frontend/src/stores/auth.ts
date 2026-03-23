import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import api, { API_BASE } from '@/api/client'
import router from '@/router'

interface User {
  id: string
  email: string
  role: string
  agencyId: string
  clientId: string | null
  displayName: string
}

interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn?: number
  user?: User
}

interface TwoFactorResponse {
  requiresTwoFactor: boolean
  email: string
  resendCooldownSeconds?: number
}

interface LoginResult {
  success: boolean
  requiresTwoFactor?: boolean
  maskedEmail?: string
  resendCooldownSeconds?: number
}

interface Resend2faResult {
  success: boolean
  resendCooldownSeconds?: number
  limitReached?: boolean
}

interface RefreshOptions {
  redirectOnFailure?: boolean
}

const DEFAULT_EXPIRES_IN_SECONDS = 3600
const REFRESH_BUFFER_SECONDS = 300
const STORAGE_KEY_ACCESS = 'accessToken'
const STORAGE_KEY_REFRESH = 'refreshToken'
const STORAGE_KEY_USER = 'user'
const STORAGE_KEY_REMEMBER = 'rememberMe'

let refreshTimer: number | null = null

// ── storage helpers ─────────────────────────────────────────

function getActiveStorage(): Storage {
  // If rememberMe was used, tokens live in localStorage; otherwise sessionStorage
  if (localStorage.getItem(STORAGE_KEY_REMEMBER) === 'true') {
    return localStorage
  }
  return sessionStorage
}

function readToken(key: string): string | null {
  return localStorage.getItem(key) ?? sessionStorage.getItem(key) ?? null
}

function readUser(): User | null {
  const raw = localStorage.getItem(STORAGE_KEY_USER) ?? sessionStorage.getItem(STORAGE_KEY_USER)
  return raw ? JSON.parse(raw) as User : null
}

function clearAllStorage() {
  for (const key of [STORAGE_KEY_ACCESS, STORAGE_KEY_REFRESH, STORAGE_KEY_USER, STORAGE_KEY_REMEMBER]) {
    localStorage.removeItem(key)
    sessionStorage.removeItem(key)
  }
}

function writeTokens(accessToken: string, refreshToken: string, remember: boolean) {
  const storage = remember ? localStorage : sessionStorage
  // Always clear the other storage first
  const other = remember ? sessionStorage : localStorage
  other.removeItem(STORAGE_KEY_ACCESS)
  other.removeItem(STORAGE_KEY_REFRESH)
  other.removeItem(STORAGE_KEY_USER)

  storage.setItem(STORAGE_KEY_ACCESS, accessToken)
  storage.setItem(STORAGE_KEY_REFRESH, refreshToken)

  if (remember) {
    localStorage.setItem(STORAGE_KEY_REMEMBER, 'true')
  } else {
    localStorage.removeItem(STORAGE_KEY_REMEMBER)
  }
}

function writeUser(u: User | null, remember: boolean) {
  const storage = remember ? localStorage : sessionStorage
  if (u) {
    storage.setItem(STORAGE_KEY_USER, JSON.stringify(u))
  } else {
    storage.removeItem(STORAGE_KEY_USER)
  }
}

// ── token utilities ─────────────────────────────────────────

function decodeTokenExpiry(token: string | null): number | null {
  if (!token) return null
  try {
    const payload = token.split('.')[1]
    if (!payload) return null
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
    const decoded = JSON.parse(window.atob(padded)) as { exp?: number }
    return typeof decoded.exp === 'number' ? decoded.exp : null
  } catch {
    return null
  }
}

function getRemainingLifetimeSeconds(token: string | null): number | null {
  const exp = decodeTokenExpiry(token)
  if (!exp) return null
  return Math.max(exp - Math.floor(Date.now() / 1000), 0)
}

function clearRefreshTimer() {
  if (refreshTimer !== null) {
    window.clearTimeout(refreshTimer)
    refreshTimer = null
  }
}

// ── store ───────────────────────────────────────────────────

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(readToken(STORAGE_KEY_ACCESS))
  const refreshToken = ref<string | null>(readToken(STORAGE_KEY_REFRESH))
  const user = ref<User | null>(readUser())
  const loading = ref(false)
  const error = ref<string | null>(null)
  const rememberMe = ref(localStorage.getItem(STORAGE_KEY_REMEMBER) === 'true')

  // 2FA transient state (not persisted)
  const twoFactorPending = ref(false)
  const twoFactorEmail = ref('')         // masked email for display
  const twoFactorRealEmail = ref('')     // actual email for verify/resend calls
  const twoFactorResendLimitReached = ref(false)

  const isAuthenticated = computed(() => !!accessToken.value && !!user.value)
  const userRole = computed(() => user.value?.role || '')
  const agencyId = computed(() => user.value?.agencyId || '')
  const clientId = computed(() => user.value?.clientId || null)
  const isClientUser = computed(() => user.value?.role === 'CLIENT_USER')
  const displayName = computed(() => user.value?.displayName || user.value?.email || '')

  function persistUser(nextUser: User | null) {
    user.value = nextUser
    writeUser(nextUser, rememberMe.value)
  }

  function setTokens(nextAccessToken: string, nextRefreshToken: string, expiresIn = DEFAULT_EXPIRES_IN_SECONDS) {
    accessToken.value = nextAccessToken
    refreshToken.value = nextRefreshToken
    writeTokens(nextAccessToken, nextRefreshToken, rememberMe.value)
    api.defaults.headers.common.Authorization = `Bearer ${nextAccessToken}`
    scheduleTokenRefresh(expiresIn)
  }

  async function redirectToLogin() {
    if (router.currentRoute.value.path !== '/login') {
      await router.push('/login')
    }
  }

  function scheduleTokenRefresh(expiresIn = DEFAULT_EXPIRES_IN_SECONDS) {
    clearRefreshTimer()
    if (!refreshToken.value) return
    const refreshDelayMs = Math.max((expiresIn - REFRESH_BUFFER_SECONDS) * 1000, 0)
    refreshTimer = window.setTimeout(() => {
      void refreshAccessToken({ redirectOnFailure: true })
    }, refreshDelayMs)
  }

  function initializeAuth() {
    if (accessToken.value) {
      api.defaults.headers.common.Authorization = `Bearer ${accessToken.value}`
    }
    if (!refreshToken.value) {
      clearRefreshTimer()
      return
    }
    const remainingLifetime = getRemainingLifetimeSeconds(accessToken.value)
    if (remainingLifetime !== null && remainingLifetime <= REFRESH_BUFFER_SECONDS) {
      void refreshAccessToken({ redirectOnFailure: true })
      return
    }
    scheduleTokenRefresh(remainingLifetime ?? DEFAULT_EXPIRES_IN_SECONDS)
  }

  /**
   * Login — returns LoginResult with 2FA state information.
   */
  async function login(email: string, password: string, remember = false): Promise<LoginResult> {
    loading.value = true
    error.value = null
    twoFactorPending.value = false
    twoFactorResendLimitReached.value = false
    rememberMe.value = remember

    try {
      const res = await api.post('/auth/login', { email, password, rememberMe: remember })

      // Check if 2FA is required
      if (res.data.requiresTwoFactor) {
        const tfRes = res.data as TwoFactorResponse
        twoFactorPending.value = true
        twoFactorEmail.value = tfRes.email
        twoFactorRealEmail.value = email
        twoFactorResendLimitReached.value = false
        return {
          success: false,
          requiresTwoFactor: true,
          maskedEmail: tfRes.email,
          resendCooldownSeconds: tfRes.resendCooldownSeconds ?? 60
        }
      }

      // Normal login — tokens received
      const authRes = res.data as AuthResponse
      setTokens(authRes.accessToken, authRes.refreshToken, authRes.expiresIn ?? DEFAULT_EXPIRES_IN_SECONDS)
      persistUser(authRes.user ?? null)
      return { success: true }
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string; remainingAttempts?: number } } }
      const msg = err.response?.data?.message || 'Login failed'
      const remaining = err.response?.data?.remainingAttempts
      error.value = remaining !== undefined ? `${msg} ${remaining} attempts remaining.` : msg
      return { success: false }
    } finally {
      loading.value = false
    }
  }

  /**
   * Verify 2FA code — complete the login flow.
   */
  async function verify2fa(code: string): Promise<boolean> {
    loading.value = true
    error.value = null
    try {
      const res = await api.post<AuthResponse>('/auth/verify-2fa', {
        email: twoFactorRealEmail.value,
        code,
        rememberMe: rememberMe.value
      })
      setTokens(res.data.accessToken, res.data.refreshToken, res.data.expiresIn ?? DEFAULT_EXPIRES_IN_SECONDS)
      persistUser(res.data.user ?? null)
      twoFactorPending.value = false
      twoFactorResendLimitReached.value = false
      return true
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string; remainingAttempts?: number } } }
      const msg = err.response?.data?.message || 'Verification failed'
      const remaining = err.response?.data?.remainingAttempts
      error.value = remaining !== undefined ? `${msg} (${remaining} attempts remaining)` : msg
      return false
    } finally {
      loading.value = false
    }
  }

  /**
   * Resend 2FA code.
   */
  async function resend2fa(): Promise<Resend2faResult> {
    error.value = null
    try {
      const res = await api.post<{ resendCooldownSeconds?: number }>('/auth/resend-2fa', { email: twoFactorRealEmail.value })
      twoFactorResendLimitReached.value = false
      return { success: true, resendCooldownSeconds: res.data.resendCooldownSeconds ?? 60 }
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string; code?: string } } }
      if (err.response?.data?.code === 'RESEND_LIMIT') {
        twoFactorResendLimitReached.value = true
      }
      error.value = err.response?.data?.message || 'Could not resend code'
      return { success: false, limitReached: twoFactorResendLimitReached.value }
    }
  }

  function cancelTwoFactor() {
    twoFactorPending.value = false
    twoFactorEmail.value = ''
    twoFactorRealEmail.value = ''
    twoFactorResendLimitReached.value = false
    error.value = null
  }

  async function refreshAccessToken(options: RefreshOptions = {}) {
    if (!refreshToken.value) {
      logout()
      if (options.redirectOnFailure) await redirectToLogin()
      return null
    }
    try {
      const res = await axios.post<AuthResponse>(`${API_BASE}/auth/refresh`, { refreshToken: refreshToken.value })
      setTokens(res.data.accessToken, res.data.refreshToken, res.data.expiresIn ?? DEFAULT_EXPIRES_IN_SECONDS)
      if (res.data.user) persistUser(res.data.user)
      return res.data.accessToken
    } catch {
      logout()
      if (options.redirectOnFailure) await redirectToLogin()
      return null
    }
  }

  async function fetchMe() {
    try {
      const res = await api.get('/auth/me')
      user.value = res.data
      writeUser(res.data, rememberMe.value)
    } catch {
      logout()
    }
  }

  function logout() {
    clearRefreshTimer()
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    error.value = null
    twoFactorPending.value = false
    twoFactorEmail.value = ''
    twoFactorRealEmail.value = ''
    twoFactorResendLimitReached.value = false
    clearAllStorage()
    delete api.defaults.headers.common.Authorization
  }

  initializeAuth()

  return {
    accessToken, refreshToken, user, loading, error, rememberMe,
    twoFactorPending, twoFactorEmail, twoFactorResendLimitReached,
    isAuthenticated, userRole, agencyId, clientId, isClientUser, displayName,
    setTokens, login, verify2fa, resend2fa, cancelTwoFactor,
    refreshAccessToken, fetchMe, logout, initializeAuth
  }
})
