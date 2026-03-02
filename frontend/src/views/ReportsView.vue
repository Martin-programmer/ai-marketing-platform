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
              v-if="item.status === 'APPROVED'"
              size="small"
              variant="text"
              color="primary"
              title="Send report"
              @click="store.sendReport(item.id)"
            >
              <v-icon>mdi-send</v-icon>
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
          <v-btn color="primary" @click="onGenerate">Generate</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useReportStore } from '@/stores/reports'
import { useClientStore } from '@/stores/clients'

const store = useReportStore()
const clientStore = useClientStore()
const selectedClient = ref<string | null>(null)
const showGenerate = ref(false)
const genForm = ref({ reportType: 'WEEKLY', periodStart: '', periodEnd: '' })

const reportHeaders = [
  { title: 'Type', key: 'reportType' },
  { title: 'Period Start', key: 'periodStart' },
  { title: 'Period End', key: 'periodEnd' },
  { title: 'Status', key: 'status' },
  { title: 'Created', key: 'createdAt' },
  { title: 'Sent At', key: 'sentAt' },
  { title: 'Actions', key: 'actions', sortable: false },
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
  await store.generateReport(selectedClient.value, genForm.value)
  showGenerate.value = false
  genForm.value = { reportType: 'WEEKLY', periodStart: '', periodEnd: '' }
}

onMounted(() => clientStore.fetchClients())
</script>
