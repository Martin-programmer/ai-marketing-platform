import api from './client'

export const ownerApi = {
  getDashboard: () => api.get('/owner/dashboard'),
  listAgencies: () => api.get('/owner/agencies'),
  createAgency: (data: { name: string; planCode?: string }) =>
    api.post('/owner/agencies', data),
  updateAgency: (id: string, data: Record<string, unknown>) =>
    api.patch(`/owner/agencies/${id}`, data),
  listAgencyUsers: (agencyId: string) =>
    api.get(`/owner/agencies/${agencyId}/users`),
  getIntelligence: () => api.get('/owner/intelligence'),
}
