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

export const useSuggestionStore = defineStore('suggestions', () => {
  const suggestions = ref<Suggestion[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

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

  async function approveSuggestion(suggestionId: string) {
    const { data } = await api.post(`/suggestions/${suggestionId}/approve`)
    const idx = suggestions.value.findIndex(s => s.id === suggestionId)
    if (idx >= 0) suggestions.value[idx] = data
  }

  async function rejectSuggestion(suggestionId: string) {
    const { data } = await api.post(`/suggestions/${suggestionId}/reject`)
    const idx = suggestions.value.findIndex(s => s.id === suggestionId)
    if (idx >= 0) suggestions.value[idx] = data
  }

  async function applySuggestion(suggestionId: string) {
    const { data } = await api.post(`/suggestions/${suggestionId}/apply`)
    const idx = suggestions.value.findIndex(s => s.id === suggestionId)
    if (idx >= 0) suggestions.value[idx] = data
  }

  return { suggestions, loading, error, fetchSuggestions, approveSuggestion, rejectSuggestion, applySuggestion }
})
