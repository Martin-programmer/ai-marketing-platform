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

  return { clients, loading, error, fetchClients, createClient, pauseClient, activateClient }
})
