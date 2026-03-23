import api from './client'

// ── System Settings ──────────────────────────────────────

export const settingsApi = {
  getAll: () => api.get('/owner/settings'),
  getByCategory: (category: string) => api.get(`/owner/settings/category/${category}`),
  getByKey: (key: string) => api.get(`/owner/settings/${key}`),
  update: (key: string, value: string) => api.put(`/owner/settings/${key}`, { value }),
  clearCache: () => api.post('/owner/settings/cache/clear'),
}

// ── AI Prompt Templates ──────────────────────────────────

export const promptsApi = {
  getAll: () => api.get('/owner/prompts'),
  getByModule: (module: string) => api.get(`/owner/prompts/${module}`),
  getPrompt: (module: string, promptName: string) =>
    api.get(`/owner/prompts/${module}/${promptName}`),
  update: (module: string, promptName: string, promptText: string) =>
    api.put(`/owner/prompts/${module}/${promptName}`, { promptText }),
  getHistory: (module: string, promptName: string) =>
    api.get(`/owner/prompts/${module}/${promptName}/history`),
  revert: (module: string, promptName: string, version: number) =>
    api.post(`/owner/prompts/${module}/${promptName}/revert/${version}`),
  clearCache: () => api.post('/owner/prompts/cache/clear'),
}

// ── Email Templates ──────────────────────────────────────

export const emailTemplatesApi = {
  getAll: () => api.get('/owner/email-templates'),
  getByKey: (key: string) => api.get(`/owner/email-templates/${key}`),
  update: (key: string, subject: string, htmlBody: string) =>
    api.put(`/owner/email-templates/${key}`, { subject, htmlBody }),
  preview: (key: string) => api.post(`/owner/email-templates/${key}/preview`),
  clearCache: () => api.post('/owner/email-templates/cache/clear'),
}
