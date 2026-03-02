import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

export interface Report {
  id: string
  agencyId: string
  clientId: string
  reportType: string
  periodStart: string
  periodEnd: string
  status: string
  htmlContent: string | null
  pdfS3Key: string | null
  createdBy: string
  approvedBy: string | null
  createdAt: string
  approvedAt: string | null
  sentAt: string | null
}

export interface Feedback {
  id: string
  agencyId: string
  clientId: string
  entityType: string
  entityId: string
  rating: number
  comment: string | null
  createdAt: string
}

export const useReportStore = defineStore('reports', () => {
  const reports = ref<Report[]>([])
  const feedback = ref<Feedback[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchReports(clientId: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get(`/clients/${clientId}/reports`)
      reports.value = data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  async function generateReport(clientId: string, payload: { reportType: string; periodStart: string; periodEnd: string }) {
    const { data } = await api.post(`/clients/${clientId}/reports/generate`, payload)
    reports.value.push(data)
    return data
  }

  async function sendReport(reportId: string) {
    const { data } = await api.post(`/reports/${reportId}/send`)
    const idx = reports.value.findIndex(r => r.id === reportId)
    if (idx >= 0) reports.value[idx] = data
  }

  async function fetchFeedback(clientId: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get(`/clients/${clientId}/feedback`)
      feedback.value = data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  async function createFeedback(clientId: string, payload: { entityType: string; entityId: string; rating: number; comment?: string }) {
    const { data } = await api.post(`/clients/${clientId}/feedback`, payload)
    feedback.value.push(data)
    return data
  }

  return { reports, feedback, loading, error, fetchReports, generateReport, sendReport, fetchFeedback, createFeedback }
})
