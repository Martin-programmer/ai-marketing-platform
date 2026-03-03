import api from './client'

export const portalApi = {
  getMyClient: () => api.get('/portal/me/client'),
  getMyProfile: () => api.get('/portal/me/client/profile'),
  getReports: () => api.get('/portal/reports'),
  getReport: (id: string) => api.get(`/portal/reports/${id}`),
  getKpis: (params?: { from?: string; to?: string }) =>
    api.get('/portal/dashboard/kpis', { params }),
  getCampaigns: () => api.get('/portal/campaigns'),
  getSuggestions: () => api.get('/portal/suggestions'),
  submitFeedback: (reportId: string, data: { rating: number; comment?: string }) =>
    api.post(`/portal/reports/${reportId}/feedback`, data),
}

export default portalApi
