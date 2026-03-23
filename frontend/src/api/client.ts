import axios, { AxiosError, AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'

export const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

type QueuedRequest = {
  resolve: (token: string) => void
  reject: (error: unknown) => void
}

interface RetryableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
}

let isRefreshing = false
let failedQueue: QueuedRequest[] = []

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach(({ resolve, reject }) => {
    if (token) {
      resolve(token)
      return
    }
    reject(error)
  })
  failedQueue = []
}

function setAuthorizationHeader(headers: RetryableRequestConfig['headers'], token: string) {
  const resolvedHeaders = AxiosHeaders.from(headers ?? {})
  resolvedHeaders.set('Authorization', `Bearer ${token}`)
  return resolvedHeaders
}

async function getAuthStore() {
  const [{ useAuthStore }, { pinia }] = await Promise.all([
    import('@/stores/auth'),
    import('@/pinia')
  ])
  return useAuthStore(pinia)
}

async function redirectToLogin() {
  const [authStore, routerModule] = await Promise.all([
    getAuthStore(),
    import('@/router')
  ])

  authStore.logout()

  const router = routerModule.default
  if (router.currentRoute.value.path !== '/login') {
    await router.push('/login')
  }
}

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json'
  }
})

// Request interceptor — adds JWT token (checks both storages for dual-storage support)
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken') ?? sessionStorage.getItem('accessToken')
  if (token) {
    config.headers = setAuthorizationHeader(config.headers, token)
  }
  return config
})

// Response interceptor — queue 401s while a single refresh is in flight
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const axiosError = error as AxiosError
    const originalRequest = axiosError.config as RetryableRequestConfig | undefined

    if (!originalRequest || axiosError.response?.status !== 401) {
      return Promise.reject(error)
    }

    const requestUrl = originalRequest.url || ''
    const isAuthEndpoint = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/refresh')

    if (isAuthEndpoint) {
      await redirectToLogin()
      return Promise.reject(error)
    }

    if (originalRequest._retry) {
      return Promise.reject(error)
    }

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject })
      }).then((token) => {
        originalRequest.headers = setAuthorizationHeader(originalRequest.headers, token)
        return api(originalRequest)
      })
    }

    originalRequest._retry = true
    isRefreshing = true

    try {
      const authStore = await getAuthStore()
      const newToken = await authStore.refreshAccessToken({ redirectOnFailure: true })

      if (!newToken) {
        throw new Error('Unable to refresh access token')
      }

      api.defaults.headers.common.Authorization = `Bearer ${newToken}`
      processQueue(null, newToken)

      originalRequest.headers = setAuthorizationHeader(originalRequest.headers, newToken)
      return api(originalRequest)
    } catch (refreshError) {
      processQueue(refreshError, null)
      return Promise.reject(refreshError)
    } finally {
      isRefreshing = false
    }

    return Promise.reject(error)
  }
)

export default api
