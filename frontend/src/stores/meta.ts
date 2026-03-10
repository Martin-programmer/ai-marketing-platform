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
  status: string
  connectedAt: string | null
  lastSyncAt: string | null
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
  async function startConnect(clientId: string): Promise<boolean> {
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
      return new Promise<boolean>((resolve) => {
        const timeoutId = window.setTimeout(() => {
          window.removeEventListener('message', handler)
          if (popup && !popup.closed) popup.close()
          error.value = 'Connection timed out'
          resolve(false)
        }, 300_000)

        const handler = (event: MessageEvent) => {
          if (event.data?.type === 'META_OAUTH_RESULT') {
            window.removeEventListener('message', handler)
            window.clearTimeout(timeoutId)
            if (event.data.success) {
              fetchConnection(clientId)
              resolve(true)
            } else {
              error.value = event.data.message || 'Connection failed'
              resolve(false)
            }
          }
        }
        window.addEventListener('message', handler)
      })
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to start connection'
      return false
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
    fetchConnection, startConnect, disconnect,
    fetchSyncJobs, triggerSync
  }
})
