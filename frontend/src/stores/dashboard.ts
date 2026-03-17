import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

export interface KpiSummary {
  totalImpressions: number
  totalClicks: number
  totalSpend: number
  totalConversions: number
  avgCtr: number
  avgCpc: number
  avgRoas: number
}

export interface AiStoredResult {
  id: string
  createdAt: string
  preview: string
  data: any
}

export const useDashboardStore = defineStore('dashboard', () => {
  const kpis = ref<KpiSummary | null>(null)
  const clients = ref<any[]>([])
  const campaigns = ref<any[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // Budget Strategist
  const budgetAnalysis = ref<any>(null)
  const budgetAnalysisHistory = ref<AiStoredResult[]>([])
  const budgetLoading = ref(false)

  // Audience Architect
  const audienceSuggestions = ref<any>(null)
  const audienceSuggestionHistory = ref<AiStoredResult[]>([])
  const audienceLoading = ref(false)

  // Anomaly Detector
  const anomalies = ref<any>(null)
  const anomalyLoading = ref(false)
  const highAnomalyCount = ref(0)

  async function fetchDashboard(clientId: string) {
    loading.value = true
    error.value = null
    try {
      const now = new Date()
      const from = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().split('T')[0]
      const to = now.toISOString().split('T')[0]

      const [kpiRes, campaignRes] = await Promise.all([
        api.get(`/clients/${clientId}/kpis?from=${from}&to=${to}`).catch(() => ({ data: null })),
        api.get(`/clients/${clientId}/campaigns`).catch(() => ({ data: [] })),
      ])
      kpis.value = kpiRes.data
      campaigns.value = campaignRes.data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  async function fetchClients() {
    const { data } = await api.get('/clients')
    clients.value = data
  }

  async function fetchBudgetAnalysis(clientId: string) {
    budgetLoading.value = true
    try {
      const { data } = await api.get(`/clients/${clientId}/ai-budget-analysis`)
      budgetAnalysis.value = data
      if (!data?.error) {
        await loadBudgetAnalysisHistory(clientId, false)
      }
      return data
    } catch (e: any) {
      budgetAnalysis.value = { error: e.response?.data?.message || e.message }
      return budgetAnalysis.value
    } finally {
      budgetLoading.value = false
    }
  }

  async function loadBudgetAnalysisHistory(clientId: string, useLoading = true) {
    if (useLoading) budgetLoading.value = true
    try {
      const { data } = await api.get(`/clients/${clientId}/ai-budget-analyses`)
      budgetAnalysisHistory.value = data || []
      budgetAnalysis.value = budgetAnalysisHistory.value[0]?.data ?? null
      return budgetAnalysisHistory.value
    } catch (e: any) {
      budgetAnalysisHistory.value = []
      budgetAnalysis.value = { error: e.response?.data?.message || e.message }
      return []
    } finally {
      if (useLoading) budgetLoading.value = false
    }
  }

  async function fetchBudgetAnalysisById(id: string) {
    budgetLoading.value = true
    try {
      const { data } = await api.get(`/ai-budget-analyses/${id}`)
      budgetAnalysis.value = data?.data ?? data
      return data
    } catch (e: any) {
      budgetAnalysis.value = { error: e.response?.data?.message || e.message }
      return budgetAnalysis.value
    } finally {
      budgetLoading.value = false
    }
  }

  async function fetchAudienceSuggestions(clientId: string) {
    audienceLoading.value = true
    try {
      const { data } = await api.post(`/clients/${clientId}/ai-audiences`)
      audienceSuggestions.value = data
      if (!data?.error) {
        await loadAudienceSuggestionHistory(clientId, false)
      }
      return data
    } catch (e: any) {
      audienceSuggestions.value = { error: e.response?.data?.message || e.message }
      return audienceSuggestions.value
    } finally {
      audienceLoading.value = false
    }
  }

  async function loadAudienceSuggestionHistory(clientId: string, useLoading = true) {
    if (useLoading) audienceLoading.value = true
    try {
      const { data } = await api.get(`/clients/${clientId}/ai-audiences/history`)
      audienceSuggestionHistory.value = data || []
      audienceSuggestions.value = audienceSuggestionHistory.value[0]?.data ?? null
      return audienceSuggestionHistory.value
    } catch (e: any) {
      audienceSuggestionHistory.value = []
      audienceSuggestions.value = { error: e.response?.data?.message || e.message }
      return []
    } finally {
      if (useLoading) audienceLoading.value = false
    }
  }

  async function fetchAudienceSuggestionById(id: string) {
    audienceLoading.value = true
    try {
      const { data } = await api.get(`/ai-audiences/${id}`)
      audienceSuggestions.value = data?.data ?? data
      return data
    } catch (e: any) {
      audienceSuggestions.value = { error: e.response?.data?.message || e.message }
      return audienceSuggestions.value
    } finally {
      audienceLoading.value = false
    }
  }

  async function runAnomalyCheck(clientId: string) {
    anomalyLoading.value = true
    try {
      const { data } = await api.post(`/clients/${clientId}/ai-anomaly-check`)
      anomalies.value = data
      highAnomalyCount.value = (data.details || []).filter((d: any) => d.riskLevel === 'HIGH').length
    } catch (e: any) {
      anomalies.value = { error: e.response?.data?.message || e.message }
    } finally {
      anomalyLoading.value = false
    }
  }

  return {
    kpis, clients, campaigns, loading, error,
    budgetAnalysis, budgetAnalysisHistory, budgetLoading,
    audienceSuggestions, audienceSuggestionHistory, audienceLoading,
    anomalies, anomalyLoading, highAnomalyCount,
    fetchDashboard, fetchClients,
    fetchBudgetAnalysis, loadBudgetAnalysisHistory, fetchBudgetAnalysisById,
    fetchAudienceSuggestions, loadAudienceSuggestionHistory, fetchAudienceSuggestionById,
    runAnomalyCheck,
  }
})
