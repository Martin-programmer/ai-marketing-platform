<template>
  <div>
    <!-- Header -->
    <div class="d-flex align-center mb-6">
      <div>
        <h1 class="text-h4 font-weight-bold">Reports</h1>
        <p class="text-body-1 text-medium-emphasis mt-1">View your campaign reports and provide feedback</p>
      </div>
    </div>

    <!-- Loading -->
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- Empty state -->
    <v-alert v-if="!loading && reports.length === 0 && !error" type="info" variant="tonal">
      No reports available yet. Reports will appear here once your agency shares them.
    </v-alert>

    <!-- Reports Table -->
    <v-card v-if="reports.length > 0" variant="outlined">
      <v-data-table
        :headers="headers"
        :items="reports"
        item-value="id"
        hover
      >
        <template #item.reportType="{ item }">
          <v-chip size="small" variant="tonal" color="primary">{{ item.reportType }}</v-chip>
        </template>

        <template #item.period="{ item }">
          {{ item.periodStart }} — {{ item.periodEnd }}
        </template>

        <template #item.status="{ item }">
          <v-chip :color="statusColor(item.status)" size="small">{{ item.status }}</v-chip>
        </template>

        <template #item.createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>

        <template #item.actions="{ item }">
          <v-btn size="small" variant="text" color="primary" @click="viewReport(item)">
            <v-icon start size="16">mdi-eye</v-icon>
            View
          </v-btn>
          <v-btn size="small" variant="text" color="teal" @click="openFeedbackDialog(item)">
            <v-icon start size="16">mdi-star</v-icon>
            Feedback
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <!-- Report Detail Dialog -->
    <v-dialog v-model="detailDialog" max-width="800" scrollable>
      <v-card v-if="selectedReport">
        <v-card-title class="d-flex align-center">
          <span>{{ selectedReport.reportType }} Report</span>
          <v-spacer />
          <v-btn icon variant="text" @click="detailDialog = false">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-card-title>
        <v-divider />
        <v-card-text>
          <div class="mb-4">
            <v-chip size="small" color="primary" class="mr-2">{{ selectedReport.reportType }}</v-chip>
            <v-chip :color="statusColor(selectedReport.status)" size="small" class="mr-2">{{ selectedReport.status }}</v-chip>
            <span class="text-body-2 text-medium-emphasis">
              {{ selectedReport.periodStart }} — {{ selectedReport.periodEnd }}
            </span>
          </div>
          <div v-if="selectedReport.htmlContent" v-html="selectedReport.htmlContent" class="report-content" />
          <v-alert v-else type="info" variant="tonal">No content available for this report.</v-alert>
        </v-card-text>
      </v-card>
    </v-dialog>

    <!-- Feedback Dialog -->
    <v-dialog v-model="feedbackDialog" max-width="500" persistent>
      <v-card>
        <v-card-title>Report Feedback</v-card-title>
        <v-card-subtitle v-if="feedbackReport">
          {{ feedbackReport.reportType }} — {{ feedbackReport.periodStart }} to {{ feedbackReport.periodEnd }}
        </v-card-subtitle>
        <v-divider />
        <v-card-text>
          <div class="text-center mb-4">
            <p class="text-body-1 mb-2">How would you rate this report?</p>
            <v-rating
              v-model="feedbackRating"
              color="amber"
              hover
              size="40"
            />
          </div>
          <v-textarea
            v-model="feedbackComment"
            label="Comment (optional)"
            variant="outlined"
            rows="3"
            counter="500"
            maxlength="500"
          />
          <v-alert v-if="feedbackError" type="error" variant="tonal" density="compact" class="mt-2">
            {{ feedbackError }}
          </v-alert>
          <v-alert v-if="feedbackSuccess" type="success" variant="tonal" density="compact" class="mt-2">
            Thank you! Your feedback has been submitted.
          </v-alert>
        </v-card-text>
        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="closeFeedbackDialog">Cancel</v-btn>
          <v-btn
            color="primary"
            variant="elevated"
            @click="submitFeedback"
            :loading="submittingFeedback"
            :disabled="feedbackRating === 0 || feedbackSuccess"
          >
            Submit Feedback
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import portalApi from '@/api/portal'

interface ReportItem {
  id: string
  reportType: string
  periodStart: string
  periodEnd: string
  status: string
  htmlContent: string | null
  createdAt: string
}

const reports = ref<ReportItem[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// Detail dialog
const detailDialog = ref(false)
const selectedReport = ref<ReportItem | null>(null)

// Feedback dialog
const feedbackDialog = ref(false)
const feedbackReport = ref<ReportItem | null>(null)
const feedbackRating = ref(0)
const feedbackComment = ref('')
const submittingFeedback = ref(false)
const feedbackError = ref<string | null>(null)
const feedbackSuccess = ref(false)

const headers = [
  { title: 'Type', key: 'reportType', sortable: true },
  { title: 'Period', key: 'period', sortable: false },
  { title: 'Status', key: 'status', sortable: true },
  { title: 'Created', key: 'createdAt', sortable: true },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const },
]

function statusColor(status: string): string {
  const map: Record<string, string> = {
    SENT: 'success',
    APPROVED: 'info',
    DRAFT: 'grey',
  }
  return map[status] || 'grey'
}

function formatDate(dt: string): string {
  if (!dt) return '—'
  return new Date(dt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

function viewReport(report: ReportItem) {
  selectedReport.value = report
  detailDialog.value = true
}

function openFeedbackDialog(report: ReportItem) {
  feedbackReport.value = report
  feedbackRating.value = 0
  feedbackComment.value = ''
  feedbackError.value = null
  feedbackSuccess.value = false
  feedbackDialog.value = true
}

function closeFeedbackDialog() {
  feedbackDialog.value = false
  feedbackReport.value = null
}

async function submitFeedback() {
  if (!feedbackReport.value || feedbackRating.value === 0) return
  submittingFeedback.value = true
  feedbackError.value = null
  try {
    await portalApi.submitFeedback(feedbackReport.value.id, {
      rating: feedbackRating.value,
      comment: feedbackComment.value || undefined,
    })
    feedbackSuccess.value = true
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    feedbackError.value = err.response?.data?.message || 'Failed to submit feedback'
  } finally {
    submittingFeedback.value = false
  }
}

async function loadReports() {
  loading.value = true
  error.value = null
  try {
    const res = await portalApi.getReports()
    reports.value = res.data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err.response?.data?.message || 'Failed to load reports'
  } finally {
    loading.value = false
  }
}

onMounted(loadReports)
</script>

<style scoped>
.report-content {
  line-height: 1.6;
}
</style>
