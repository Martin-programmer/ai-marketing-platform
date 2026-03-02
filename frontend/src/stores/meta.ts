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
  connectionId: string
  jobType: string
  status: string
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
  createdAt: string
}

export const useMetaStore = defineStore('meta', () => {
  const connection = ref<MetaConnection | null>(null)
  const syncStatus = ref<MetaSyncJob | null>(null)
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

  async function connectStart(clientId: string) {
    const { data } = await api.post(`/clients/${clientId}/meta/connect/start`)
    return data
  }

  async function disconnect(clientId: string) {
    await api.post(`/clients/${clientId}/meta/disconnect`)
    connection.value = null
  }

  async function fetchSyncStatus(clientId: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get(`/clients/${clientId}/meta/sync/status`)
      syncStatus.value = data
    } catch (e: any) {
      error.value = e.message
      syncStatus.value = null
    } finally {
      loading.value = false
    }
  }

  async function triggerSync(clientId: string, jobType: string) {
    const { data } = await api.post(`/clients/${clientId}/meta/sync/${jobType.toLowerCase()}`)
    syncStatus.value = data
    return data
  }

  return { connection, syncStatus, loading, error, fetchConnection, connectStart, disconnect, fetchSyncStatus, triggerSync }
})
