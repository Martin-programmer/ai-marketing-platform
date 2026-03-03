import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

interface Agency {
  id: string
  name: string
  status: string
  planCode: string
  createdAt: string
  updatedAt: string
}

interface CreateAgencyPayload {
  name: string
  planCode: string
  adminEmail: string
  adminPassword: string
  adminDisplayName: string
}

export const useAdminStore = defineStore('admin', () => {
  const agencies = ref<Agency[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchAgencies() {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get('/admin/agencies')
      agencies.value = data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to fetch agencies'
    } finally {
      loading.value = false
    }
  }

  async function createAgency(payload: CreateAgencyPayload) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.post('/admin/agencies', payload)
      agencies.value.push(data.agency)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to create agency'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function suspendAgency(agencyId: string) {
    try {
      const { data } = await api.post(`/admin/agencies/${agencyId}/suspend`)
      const idx = agencies.value.findIndex(a => a.id === agencyId)
      if (idx !== -1) agencies.value[idx] = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to suspend agency'
      throw e
    }
  }

  async function reactivateAgency(agencyId: string) {
    try {
      const { data } = await api.post(`/admin/agencies/${agencyId}/reactivate`)
      const idx = agencies.value.findIndex(a => a.id === agencyId)
      if (idx !== -1) agencies.value[idx] = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to reactivate agency'
      throw e
    }
  }

  return {
    agencies, loading, error,
    fetchAgencies, createAgency, suspendAgency, reactivateAgency
  }
})
