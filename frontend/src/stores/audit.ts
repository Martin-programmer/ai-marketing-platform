import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

export interface AuditLogEntry {
  id: string
  agencyId: string
  clientId: string | null
  actorUserId: string | null
  actorRole: string
  action: string
  entityType: string
  entityId: string | null
  beforeJson: string | null
  afterJson: string | null
  correlationId: string
  ip: string | null
  userAgent: string | null
  createdAt: string
}

export const useAuditStore = defineStore('audit', () => {
  const logs = ref<AuditLogEntry[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchLogs(params?: {
    entityType?: string
    action?: string
    entityId?: string
    clientId?: string
    limit?: number
  }) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get('/audit-logs', { params })
      logs.value = data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  return { logs, loading, error, fetchLogs }
})
