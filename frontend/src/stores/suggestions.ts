import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

export interface Suggestion {
  id: string
  agencyId: string
  clientId: string
  scopeType: string
  scopeId: string
  suggestionType: string
  payloadJson: any
  rationale: string | null
  confidence: number
  riskLevel: string
  status: string
  cooldownUntil: string | null
  createdBy: string
  createdAt: string
  reviewedBy: string | null
  reviewedAt: string | null
}

export interface SuggestionActionLog {
  id: string
  agencyId: string
  clientId: string
  suggestionId: string
  executedBy: string
  metaRequestJson: string | null
  metaResponseJson: string | null
  success: boolean
  resultSnapshotJson: string | null
  createdAt: string
}

export interface SuggestionFeedback {
  id: string
  agencyId: string
  clientId: string
  sourceType: string
  sourceId: string
  rating: number
  comment: string | null
  createdBy: string
  createdAt: string
}

export const useSuggestionStore = defineStore('suggestions', () => {
  const suggestions = ref<Suggestion[]>([])
  const actionLoadingById = ref<Record<string, boolean>>({})
  const actionLogsBySuggestion = ref<Record<string, SuggestionActionLog[]>>({})
  const actionLogsLoadingBySuggestion = ref<Record<string, boolean>>({})
  const feedbackBySuggestion = ref<Record<string, SuggestionFeedback[]>>({})
  const feedbackSubmittingBySuggestion = ref<Record<string, boolean>>({})
  const loading = ref(false)
  const error = ref<string | null>(null)

  function upsertSuggestion(updated: Suggestion) {
    const index = suggestions.value.findIndex((item) => item.id === updated.id)
    if (index >= 0) {
      suggestions.value[index] = updated
      return
    }
    suggestions.value.unshift(updated)
  }

  async function fetchSuggestions(clientId: string, status?: string) {
    loading.value = true
    error.value = null
    try {
      const params = status ? `?status=${status}` : ''
      const { data } = await api.get(`/clients/${clientId}/suggestions${params}`)
      suggestions.value = data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  async function updateSuggestion(suggestionId: string, payload: { payloadJson?: string; confidence?: number | null }) {
    actionLoadingById.value[suggestionId] = true
    error.value = null
    try {
      const { data } = await api.patch(`/suggestions/${suggestionId}`, payload)
      upsertSuggestion(data)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      actionLoadingById.value[suggestionId] = false
    }
  }

  async function approveSuggestion(suggestionId: string) {
    actionLoadingById.value[suggestionId] = true
    error.value = null
    try {
      const { data } = await api.post(`/suggestions/${suggestionId}/approve`)
      upsertSuggestion(data)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      actionLoadingById.value[suggestionId] = false
    }
  }

  async function rejectSuggestion(suggestionId: string) {
    actionLoadingById.value[suggestionId] = true
    error.value = null
    try {
      const { data } = await api.post(`/suggestions/${suggestionId}/reject`)
      upsertSuggestion(data)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      actionLoadingById.value[suggestionId] = false
    }
  }

  async function applySuggestion(suggestionId: string) {
    actionLoadingById.value[suggestionId] = true
    error.value = null
    try {
      const { data } = await api.post(`/suggestions/${suggestionId}/apply`)
      upsertSuggestion(data)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      actionLoadingById.value[suggestionId] = false
    }
  }

  async function approveAndApplySuggestion(suggestionId: string) {
    actionLoadingById.value[suggestionId] = true
    error.value = null
    try {
      const { data } = await api.post(`/suggestions/${suggestionId}/approve-and-apply`)
      upsertSuggestion(data)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      actionLoadingById.value[suggestionId] = false
    }
  }

  async function fetchActionLogs(suggestionId: string, force = false) {
    if (!force && actionLogsBySuggestion.value[suggestionId]) {
      return actionLogsBySuggestion.value[suggestionId]
    }

    actionLogsLoadingBySuggestion.value[suggestionId] = true
    error.value = null
    try {
      const { data } = await api.get(`/suggestions/${suggestionId}/actions`)
      actionLogsBySuggestion.value[suggestionId] = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      actionLogsLoadingBySuggestion.value[suggestionId] = false
    }
  }

  async function submitFeedback(clientId: string, suggestionId: string, payload: { rating: number; comment?: string }) {
    feedbackSubmittingBySuggestion.value[suggestionId] = true
    error.value = null
    try {
      const { data } = await api.post(`/clients/${clientId}/feedback`, {
        clientId,
        entityType: 'SUGGESTION',
        entityId: suggestionId,
        rating: payload.rating,
        comment: payload.comment || null,
      })
      const existing = feedbackBySuggestion.value[suggestionId] ?? []
      feedbackBySuggestion.value[suggestionId] = [data, ...existing]
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      feedbackSubmittingBySuggestion.value[suggestionId] = false
    }
  }

  return {
    suggestions,
    actionLoadingById,
    actionLogsBySuggestion,
    actionLogsLoadingBySuggestion,
    feedbackBySuggestion,
    feedbackSubmittingBySuggestion,
    loading,
    error,
    fetchSuggestions,
    updateSuggestion,
    approveSuggestion,
    rejectSuggestion,
    applySuggestion,
    approveAndApplySuggestion,
    fetchActionLogs,
    submitFeedback,
  }
})
