import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

export interface Client {
  id: string
  agencyId: string
  name: string
  industry: string | null
  status: string
  timezone: string
  currency: string
  createdAt: string
  updatedAt: string
}

export const useClientStore = defineStore('clients', () => {
  const clients = ref<Client[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  // AI state
  const briefLoading = ref(false)
  const briefResult = ref<Record<string, any> | null>(null)
  const audienceLoading = ref(false)
  const audienceResult = ref<Record<string, any> | null>(null)

  async function fetchClients() {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get('/clients')
      clients.value = data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  async function createClient(payload: { name: string; industry?: string; timezone?: string; currency?: string }) {
    const { data } = await api.post('/clients', payload)
    clients.value.push(data)
    return data
  }

  async function pauseClient(clientId: string) {
    const { data } = await api.post(`/clients/${clientId}/pause`)
    const idx = clients.value.findIndex(c => c.id === clientId)
    if (idx >= 0) clients.value[idx] = data
  }

  async function activateClient(clientId: string) {
    const { data } = await api.post(`/clients/${clientId}/activate`)
    const idx = clients.value.findIndex(c => c.id === clientId)
    if (idx >= 0) clients.value[idx] = data
  }

  async function analyzeWebsite(clientId: string, websiteUrl: string) {
    briefLoading.value = true
    briefResult.value = null
    try {
      const { data } = await api.post(`/clients/${clientId}/ai-brief`, { websiteUrl })
      briefResult.value = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      return null
    } finally {
      briefLoading.value = false
    }
  }

  async function getLastBrief(clientId: string) {
    try {
      const { data } = await api.get(`/clients/${clientId}/ai-brief`)
      briefResult.value = data
      return data
    } catch {
      briefResult.value = null
      return null
    }
  }

  async function suggestAudiences(clientId: string) {
    audienceLoading.value = true
    audienceResult.value = null
    try {
      const { data } = await api.post(`/clients/${clientId}/ai-audiences`)
      audienceResult.value = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      return null
    } finally {
      audienceLoading.value = false
    }
  }

  return {
    clients, loading, error,
    briefLoading, briefResult, audienceLoading, audienceResult,
    fetchClients, createClient, pauseClient, activateClient,
    analyzeWebsite, getLastBrief, suggestAudiences
  }
})
