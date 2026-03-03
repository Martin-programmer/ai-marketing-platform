import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '@/api/client'

interface User {
  id: string
  email: string
  role: string
  agencyId: string
  clientId: string | null
  displayName: string
}

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string | null>(localStorage.getItem('accessToken'))
  const refreshToken = ref<string | null>(localStorage.getItem('refreshToken'))
  const user = ref<User | null>(JSON.parse(localStorage.getItem('user') || 'null'))
  const loading = ref(false)
  const error = ref<string | null>(null)

  const isAuthenticated = computed(() => !!accessToken.value && !!user.value)
  const userRole = computed(() => user.value?.role || '')
  const agencyId = computed(() => user.value?.agencyId || '')
  const clientId = computed(() => user.value?.clientId || null)
  const isClientUser = computed(() => user.value?.role === 'CLIENT_USER')
  const displayName = computed(() => user.value?.displayName || user.value?.email || '')

  async function login(email: string, password: string) {
    loading.value = true
    error.value = null
    try {
      const res = await api.post('/auth/login', { email, password })
      accessToken.value = res.data.accessToken
      refreshToken.value = res.data.refreshToken
      user.value = res.data.user

      localStorage.setItem('accessToken', res.data.accessToken)
      localStorage.setItem('refreshToken', res.data.refreshToken)
      localStorage.setItem('user', JSON.stringify(res.data.user))

      return true
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      error.value = err.response?.data?.message || 'Login failed'
      return false
    } finally {
      loading.value = false
    }
  }

  async function refreshAccessToken() {
    if (!refreshToken.value) return false
    try {
      const res = await api.post('/auth/refresh', { refreshToken: refreshToken.value })
      accessToken.value = res.data.accessToken
      localStorage.setItem('accessToken', res.data.accessToken)
      return true
    } catch {
      logout()
      return false
    }
  }

  async function fetchMe() {
    try {
      const res = await api.get('/auth/me')
      user.value = res.data
      localStorage.setItem('user', JSON.stringify(res.data))
    } catch {
      logout()
    }
  }

  function logout() {
    accessToken.value = null
    refreshToken.value = null
    user.value = null
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
  }

  return {
    accessToken, refreshToken, user, loading, error,
    isAuthenticated, userRole, agencyId, clientId, isClientUser, displayName,
    login, refreshAccessToken, fetchMe, logout
  }
})
