import api from './client'

export const portalApi = {
  getMyClient: () => api.get('/portal/me/client'),
  getMyProfile: () => api.get('/portal/me/client/profile'),
  getQuestionnaire: () => api.get('/portal/questionnaire'),
  saveQuestionnaire: (data: Record<string, any>, complete = false) =>
    api.put(`/portal/questionnaire?complete=${complete}`, data),
  getReports: () => api.get('/portal/reports'),
  getReport: (id: string) => api.get(`/portal/reports/${id}`),
  getKpis: (params?: { from?: string; to?: string }) =>
    api.get('/portal/dashboard/kpis', { params }),
  getKpiSummary: (params: { from: string; to: string }) =>
    api.get('/portal/dashboard/kpis/summary', { params }),
  getKpiDaily: (params: { from: string; to: string }) =>
    api.get('/portal/dashboard/kpis/daily', { params }),
  getTopCampaigns: (params: { from: string; to: string; limit?: number }) =>
    api.get('/portal/dashboard/kpis/top-campaigns', { params }),
  getCampaigns: () => api.get('/portal/campaigns'),
  getSuggestions: () => api.get('/portal/suggestions'),
  submitFeedback: (reportId: string, data: { rating: number; comment?: string }) =>
    api.post(`/portal/reports/${reportId}/feedback`, data),
  aiChat: (question: string) =>
    api.post('/portal/ai-chat', { question }),
}

export default portalApi
