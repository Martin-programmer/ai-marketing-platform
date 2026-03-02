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

export const useDashboardStore = defineStore('dashboard', () => {
  const kpis = ref<KpiSummary | null>(null)
  const clients = ref<any[]>([])
  const campaigns = ref<any[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

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

  return { kpis, clients, campaigns, loading, error, fetchDashboard, fetchClients }
})
