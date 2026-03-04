import api from './client'

export const permissionsApi = {
  getAvailable: () => api.get('/permissions/available'),

  getUserPermissions: (userId: string) =>
    api.get(`/permissions/users/${userId}`),

  getClientPermissions: (clientId: string) =>
    api.get(`/permissions/clients/${clientId}`),

  setPermissions: (userId: string, clientId: string, permissions: string[]) =>
    api.put(`/permissions/users/${userId}/clients/${clientId}`, { permissions }),

  removePermissions: (userId: string, clientId: string) =>
    api.delete(`/permissions/users/${userId}/clients/${clientId}`),

  applyPreset: (userId: string, clientId: string, preset: string) =>
    api.post(`/permissions/users/${userId}/clients/${clientId}/preset`, { preset }),
}
