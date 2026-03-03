import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

interface User {
  id: string
  email: string
  displayName: string
  role: string
  status: string
  agencyId: string
  clientId: string | null
  createdAt: string
  updatedAt: string
}

interface InvitePayload {
  email: string
  password: string
  displayName: string
  role: string
  clientId?: string
}

interface UpdatePayload {
  displayName?: string
  role?: string
  status?: string
}

export const useTeamStore = defineStore('team', () => {
  const users = ref<User[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchUsers(agencyId?: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get('/users', {
        params: agencyId ? { agencyId } : undefined
      })
      users.value = data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to fetch users'
    } finally {
      loading.value = false
    }
  }

  async function inviteUser(payload: InvitePayload, agencyId?: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.post('/users/invite', payload, {
        params: agencyId ? { agencyId } : undefined
      })
      users.value.push(data)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to invite user'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function updateUser(userId: string, payload: UpdatePayload, agencyId?: string) {
    try {
      const { data } = await api.patch(`/users/${userId}`, payload, {
        params: agencyId ? { agencyId } : undefined
      })
      const idx = users.value.findIndex(u => u.id === userId)
      if (idx !== -1) users.value[idx] = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to update user'
      throw e
    }
  }

  async function disableUser(userId: string, agencyId?: string) {
    try {
      const { data } = await api.post(`/users/${userId}/disable`, undefined, {
        params: agencyId ? { agencyId } : undefined
      })
      const idx = users.value.findIndex(u => u.id === userId)
      if (idx !== -1) users.value[idx] = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to disable user'
      throw e
    }
  }

  async function enableUser(userId: string, agencyId?: string) {
    try {
      const { data } = await api.post(`/users/${userId}/enable`, undefined, {
        params: agencyId ? { agencyId } : undefined
      })
      const idx = users.value.findIndex(u => u.id === userId)
      if (idx !== -1) users.value[idx] = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to enable user'
      throw e
    }
  }

  return {
    users, loading, error,
    fetchUsers, inviteUser, updateUser, disableUser, enableUser
  }
})
