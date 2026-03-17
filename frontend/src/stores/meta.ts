import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

export interface MetaConnection {
  id: string
  agencyId: string
  clientId: string
  adAccountId: string | null
  pixelId: string | null
  pageId: string | null
  tokenKeyId: string
  status: string
  connectedAt: string | null
  lastSyncAt: string | null
  tokenExpiresAt: string | null
  lastTokenRefreshAt: string | null
  tokenRefreshFailed: boolean
  daysUntilExpiry: number | null
  lastErrorCode: string | null
  lastErrorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface MetaSyncJob {
  id: string
  agencyId: string
  clientId: string
  jobType: string
  jobStatus: string
  idempotencyKey: string
  requestedAt: string | null
  startedAt: string | null
  finishedAt: string | null
  statsJson: string | null
  errorJson: string | null
}

export interface MetaAdAccountOption {
  id: string
  name: string
  accountId: string
  currency: string
  timezone: string
  status: number
  statusLabel: string
  selectable: boolean
}

export interface MetaConnectResult {
  success: boolean
  selectionRequired?: boolean
  message?: string
  clientId?: string
  adAccounts?: MetaAdAccountOption[]
}

export const useMetaStore = defineStore('meta', () => {
  const connection = ref<MetaConnection | null>(null)
  const syncJobs = ref<MetaSyncJob[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchConnection(clientId: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get(`/clients/${clientId}/meta/connection`)
      connection.value = data
    } catch (e: any) {
      error.value = e.message
      connection.value = null
    } finally {
      loading.value = false
    }
  }

  /**
   * Start Meta OAuth flow in a popup window.
   * Returns a promise that resolves to true on success, false on failure.
   */
  async function startConnect(clientId: string): Promise<MetaConnectResult> {
    loading.value = true
    error.value = null
    try {
      const res = await api.post(`/clients/${clientId}/meta/connect/start`)
      const { authorizationUrl } = res.data

      // Open Meta OAuth in popup
      const popup = window.open(
        authorizationUrl,
        'meta_oauth',
        'width=600,height=700,left=200,top=100'
      )

      // Listen for postMessage from callback page
      return new Promise<MetaConnectResult>((resolve) => {
        const timeoutId = window.setTimeout(() => {
          window.removeEventListener('message', handler)
          if (popup && !popup.closed) popup.close()
          error.value = 'Connection timed out'
          resolve({ success: false, message: 'Connection timed out' })
        }, 300_000)

        const handler = (event: MessageEvent) => {
          if (event.data?.type === 'META_OAUTH_RESULT') {
            window.removeEventListener('message', handler)
            window.clearTimeout(timeoutId)
            if (event.data.success && !event.data.selectionRequired) {
              fetchConnection(clientId)
              resolve({ success: true, message: event.data.message })
            } else if (event.data.selectionRequired) {
              resolve({
                success: true,
                selectionRequired: true,
                message: event.data.message,
                clientId: event.data.clientId,
                adAccounts: event.data.adAccounts || [],
              })
            } else {
              error.value = event.data.message || 'Connection failed'
              resolve({ success: false, message: error.value || 'Connection failed' })
            }
          }
        }
        window.addEventListener('message', handler)
      })
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to start connection'
      return { success: false, message: error.value || 'Failed to start connection' }
    } finally {
      loading.value = false
    }
  }

  async function selectAdAccount(clientId: string, adAccountId: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.post(`/clients/${clientId}/meta/connect/select-account`, { adAccountId })
      await fetchConnection(clientId)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to connect selected ad account'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function disconnect(clientId: string) {
    loading.value = true
    error.value = null
    try {
      await api.post(`/clients/${clientId}/meta/disconnect`)
      await fetchConnection(clientId)
      syncJobs.value = []
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to disconnect'
    } finally {
      loading.value = false
    }
  }

  async function fetchSyncJobs(clientId: string) {
    try {
      const { data } = await api.get(`/clients/${clientId}/meta/sync/status`)
      syncJobs.value = data
    } catch (e: any) {
      console.error('Failed to fetch sync jobs', e)
    }
  }

  async function triggerSync(clientId: string, type: 'initial' | 'daily' | 'manual') {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.post(`/clients/${clientId}/meta/sync/${type}`)
      await fetchSyncJobs(clientId)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Sync failed'
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    connection, syncJobs, loading, error,
    fetchConnection, startConnect, selectAdAccount, disconnect,
    fetchSyncJobs, triggerSync
  }
})
