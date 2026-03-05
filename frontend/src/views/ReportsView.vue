<template>
  <div>
    <h1 class="mb-4">Reports</h1>

    <v-select
      v-model="selectedClient"
      :items="clientStore.clients"
      item-title="name"
      item-value="id"
      label="Select Client"
      variant="outlined"
      density="compact"
      class="mb-4"
      style="max-width: 400px"
      @update:model-value="onClientChange"
    />

    <v-progress-linear v-if="store.loading" indeterminate color="primary" class="mb-4" />

    <v-alert v-if="store.error" type="error" class="mb-4">{{ store.error }}</v-alert>

    <v-alert v-if="!selectedClient" type="info" class="mb-4">
      Select a client to view reports.
    </v-alert>

    <template v-if="selectedClient">
      <!-- Reports Section -->
      <div class="d-flex justify-space-between align-center mb-3">
        <h2>Reports</h2>
        <v-btn color="primary" @click="showGenerate = true">
          <v-icon start>mdi-file-chart</v-icon> Generate Report
        </v-btn>
      </div>

      <v-card class="mb-6">
        <v-data-table
          :headers="reportHeaders"
          :items="store.reports"
          :loading="store.loading"
          item-value="id"
          hover
          no-data-text="No reports yet"
          class="cursor-pointer"
          @click:row="(_event: any, { item }: any) => openPreview(item)"
        >
          <template #item.status="{ item }">
            <v-chip :color="reportStatusColor(item.status)" size="small">
              {{ item.status }}
            </v-chip>
          </template>
          <template #item.periodStart="{ item }">
            {{ item.periodStart }}
          </template>
          <template #item.periodEnd="{ item }">
            {{ item.periodEnd }}
          </template>
          <template #item.createdAt="{ item }">
            {{ new Date(item.createdAt).toLocaleDateString() }}
          </template>
          <template #item.sentAt="{ item }">
            {{ item.sentAt ? new Date(item.sentAt).toLocaleDateString() : '—' }}
          </template>
          <template #item.actions="{ item }">
            <v-btn
              size="small"
              variant="text"
              color="primary"
              title="Preview report"
              @click.stop="openPreview(item)"
            >
              <v-icon>mdi-eye</v-icon>
            </v-btn>
          </template>
        </v-data-table>
      </v-card>

      <!-- Feedback Section -->
      <h2 class="mb-3">Feedback</h2>
      <div v-if="store.feedback.length === 0" class="text-grey">No feedback yet.</div>
      <v-row>
        <v-col v-for="fb in store.feedback" :key="fb.id" cols="12" sm="6" md="4">
          <v-card variant="outlined">
            <v-card-text>
              <div class="d-flex align-center mb-2">
                <v-rating
                  :model-value="fb.rating"
                  readonly
                  density="compact"
                  size="small"
                  color="amber"
                />
                <span class="ml-2 text-caption text-grey">{{ fb.entityType }} / {{ fb.entityId }}</span>
              </div>
              <p v-if="fb.comment" class="mb-1">{{ fb.comment }}</p>
              <div class="text-caption text-grey">{{ new Date(fb.createdAt).toLocaleDateString() }}</div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>
    </template>

    <!-- Generate Report Dialog -->
    <v-dialog v-model="showGenerate" max-width="500">
      <v-card title="Generate Report">
        <v-card-text>
          <v-select
            v-model="genForm.reportType"
            :items="['DAILY', 'WEEKLY', 'MONTHLY']"
            label="Report Type"
            required
          />
          <v-text-field
            v-model="genForm.periodStart"
            label="Period Start"
            type="date"
            required
          />
          <v-text-field
            v-model="genForm.periodEnd"
            label="Period End"
            type="date"
            required
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showGenerate = false">Cancel</v-btn>
          <v-btn color="primary" :loading="generating" @click="onGenerate">Generate</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Report Preview Dialog -->
    <v-dialog v-model="showPreview" max-width="1000" scrollable>
      <v-card>
        <v-card-title class="d-flex align-center">
          <span>Report Preview</span>
          <v-spacer />
          <v-chip :color="reportStatusColor(previewReport?.status ?? '')" size="small" class="mr-2">
            {{ previewReport?.status }}
          </v-chip>
          <v-btn icon size="small" @click="showPreview = false">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-card-title>
        <v-card-text style="height: 600px; padding: 0;">
          <iframe
            :srcdoc="previewReport?.htmlContent ?? undefined"
            style="width: 100%; height: 100%; border: none;"
          />
        </v-card-text>
        <v-card-actions>
          <v-btn
            variant="outlined"
            @click="showNarrativeEditor = true"
            :disabled="previewReport?.status !== 'DRAFT' && previewReport?.status !== 'IN_REVIEW'"
          >
            <v-icon start>mdi-pencil</v-icon> Edit Narrative
          </v-btn>
          <v-spacer />
          <v-btn color="primary" variant="outlined" @click="downloadPdf">
            <v-icon start>mdi-file-pdf-box</v-icon> Download PDF
          </v-btn>
          <v-btn
            color="success"
            @click="approveReport"
            :disabled="previewReport?.status !== 'DRAFT' && previewReport?.status !== 'IN_REVIEW'"
          >
            <v-icon start>mdi-check</v-icon> Approve
          </v-btn>
          <v-btn
            color="info"
            @click="sendReport"
            :disabled="previewReport?.status !== 'APPROVED'"
          >
            <v-icon start>mdi-send</v-icon> Send to Client
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Narrative Editor Dialog -->
    <v-dialog v-model="showNarrativeEditor" max-width="600">
      <v-card>
        <v-card-title>Edit Executive Summary</v-card-title>
        <v-card-text>
          <v-textarea
            v-model="narrative"
            label="Executive Summary / Narrative"
            hint="This text appears at the top of the report. Describe key highlights, changes, and recommendations."
            rows="8"
            variant="outlined"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="showNarrativeEditor = false">Cancel</v-btn>
          <v-btn color="primary" @click="saveNarrative" :loading="saving">Save &amp; Regenerate</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Snackbar -->
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="3000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useReportStore, type Report } from '@/stores/reports'
import { useClientStore } from '@/stores/clients'
import api from '@/api/client'

const store = useReportStore()
const clientStore = useClientStore()
const selectedClient = ref<string | null>(null)
const showGenerate = ref(false)
const generating = ref(false)
const genForm = ref({ reportType: 'WEEKLY', periodStart: '', periodEnd: '' })

// Preview state
const showPreview = ref(false)
const previewReport = ref<Report | null>(null)

// Narrative editor state
const showNarrativeEditor = ref(false)
const narrative = ref('')
const saving = ref(false)

// Snackbar
const snackbar = ref({ show: false, text: '', color: 'success' })

const reportHeaders = [
  { title: 'Type', key: 'reportType' },
  { title: 'Period Start', key: 'periodStart' },
  { title: 'Period End', key: 'periodEnd' },
  { title: 'Status', key: 'status' },
  { title: 'Created', key: 'createdAt' },
  { title: 'Sent At', key: 'sentAt' },
  { title: '', key: 'actions', sortable: false, width: 60 },
]

function reportStatusColor(status: string) {
  const map: Record<string, string> = { DRAFT: 'grey', IN_REVIEW: 'info', APPROVED: 'success', SENT: 'primary' }
  return map[status] || 'grey'
}

async function onClientChange(clientId: string) {
  if (clientId) {
    await Promise.all([store.fetchReports(clientId), store.fetchFeedback(clientId)])
  }
}

async function onGenerate() {
  if (!selectedClient.value) return
  generating.value = true
  try {
    const newReport = await store.generateReport(selectedClient.value, genForm.value)
    showGenerate.value = false
    genForm.value = { reportType: 'WEEKLY', periodStart: '', periodEnd: '' }
    snackbar.value = { show: true, text: 'Report generated!', color: 'success' }
    openPreview(newReport)
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || 'Generation failed', color: 'error' }
  } finally {
    generating.value = false
  }
}

function openPreview(report: Report) {
  previewReport.value = { ...report }
  narrative.value = ''
  showPreview.value = true
}

async function downloadPdf() {
  if (!previewReport.value) return
  try {
    const res = await api.get(`/reports/${previewReport.value.id}/pdf`, {
      responseType: 'blob',
    })
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `report_${previewReport.value.periodEnd}.pdf`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (e: any) {
    snackbar.value = { show: true, text: 'PDF download failed', color: 'error' }
  }
}

async function approveReport() {
  if (!previewReport.value) return
  try {
    await api.post(`/reports/${previewReport.value.id}/approve`)
    previewReport.value.status = 'APPROVED'
    snackbar.value = { show: true, text: 'Report approved!', color: 'success' }
    if (selectedClient.value) await store.fetchReports(selectedClient.value)
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || 'Approve failed', color: 'error' }
  }
}

async function sendReport() {
  if (!previewReport.value) return
  try {
    await api.post(`/reports/${previewReport.value.id}/send`)
    previewReport.value.status = 'SENT'
    snackbar.value = { show: true, text: 'Report sent to client!', color: 'success' }
    if (selectedClient.value) await store.fetchReports(selectedClient.value)
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || 'Send failed', color: 'error' }
  }
}

async function saveNarrative() {
  if (!previewReport.value) return
  saving.value = true
  try {
    const { data } = await api.patch(`/reports/${previewReport.value.id}`, {
      narrative: narrative.value,
    })
    previewReport.value = data
    showNarrativeEditor.value = false
    snackbar.value = { show: true, text: 'Report updated with new narrative!', color: 'success' }
    if (selectedClient.value) await store.fetchReports(selectedClient.value)
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || 'Save failed', color: 'error' }
  } finally {
    saving.value = false
  }
}

onMounted(() => clientStore.fetchClients())
</script>

<style scoped>
.cursor-pointer :deep(tbody tr) {
  cursor: pointer;
}
</style>
