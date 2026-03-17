<template>
  <v-container fluid>
    <v-row align="center" class="mb-4">
      <v-col>
        <h1 class="text-h4">AI Audit Dashboard</h1>
      </v-col>
      <v-col cols="auto">
        <v-btn-toggle v-model="datePreset" mandatory density="compact" color="primary" @update:model-value="onPresetChange">
          <v-btn value="today" size="small">Today</v-btn>
          <v-btn value="7d" size="small">7 Days</v-btn>
          <v-btn value="30d" size="small">30 Days</v-btn>
          <v-btn value="90d" size="small">90 Days</v-btn>
          <v-btn value="custom" size="small">Custom</v-btn>
        </v-btn-toggle>
      </v-col>
      <v-col v-if="datePreset === 'custom'" cols="auto">
        <v-text-field v-model="fromDate" label="From" type="date" density="compact" hide-details variant="outlined" class="mr-2" style="display:inline-block;width:160px" />
        <v-text-field v-model="toDate" label="To" type="date" density="compact" hide-details variant="outlined" style="display:inline-block;width:160px" />
        <v-btn color="primary" size="small" class="ml-2" @click="loadAll">Apply</v-btn>
      </v-col>
    </v-row>

    <!-- Summary Cards -->
    <v-row>
      <v-col cols="12" sm="6" md="3">
        <v-card>
          <v-card-text class="text-center">
            <div class="text-h4 font-weight-bold">{{ summary.totalCalls.toLocaleString() }}</div>
            <div class="text-body-2 text-medium-emphasis">Total Calls</div>
            <div class="text-caption">
              <v-chip size="x-small" color="success" class="mr-1">{{ summary.successfulCalls }}</v-chip>
              <v-chip size="x-small" color="error">{{ summary.failedCalls }} failed</v-chip>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card>
          <v-card-text class="text-center">
            <div class="text-h4 font-weight-bold">{{ formatTokens(summary.totalTokens) }}</div>
            <div class="text-body-2 text-medium-emphasis">Total Tokens</div>
            <div class="text-caption">
              {{ formatTokens(summary.totalPromptTokens) }} in / {{ formatTokens(summary.totalCompletionTokens) }} out
            </div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card>
          <v-card-text class="text-center">
            <div class="text-h4 font-weight-bold">${{ Number(summary.totalCostUsd).toFixed(2) }}</div>
            <div class="text-body-2 text-medium-emphasis">Total Cost</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card>
          <v-card-text class="text-center">
            <div class="text-h4 font-weight-bold">{{ Math.round(summary.avgDurationMs) }}ms</div>
            <div class="text-body-2 text-medium-emphasis">Avg Response Time</div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Charts -->
    <v-row class="mt-4">
      <v-col cols="12" md="6">
        <v-card>
          <v-card-title>Cost by Day</v-card-title>
          <v-card-text style="height: 320px">
            <Bar v-if="costByDayData.labels.length" :data="costByDayData" :options="barOptions" />
            <div v-else class="d-flex align-center justify-center" style="height:100%">
              <span class="text-medium-emphasis">No data</span>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="6">
        <v-card>
          <v-card-title>Calls by Module</v-card-title>
          <v-card-text style="height: 320px">
            <Bar v-if="callsByModuleData.labels.length" :data="callsByModuleData" :options="horizontalBarOptions" />
            <div v-else class="d-flex align-center justify-center" style="height:100%">
              <span class="text-medium-emphasis">No data</span>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Agency Breakdown -->
    <v-card class="mt-4">
      <v-card-title>Agency Breakdown</v-card-title>
      <v-data-table
        :headers="agencyHeaders"
        :items="summary.byAgency"
        :items-per-page="10"
        density="compact"
        @click:row="(_e: Event, row: { item: AgencyRow }) => toggleAgencyExpand(row.item)"
      >
        <template #item.costUsd="{ value }">
          ${{ Number(value).toFixed(2) }}
        </template>
        <template #item.actions="{ item }">
          <v-icon size="small">{{ expandedAgency === item.agencyId ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
        </template>
        <template #expanded-row="{ item }">
          <tr v-if="expandedAgency === item.agencyId">
            <td :colspan="agencyHeaders.length + 1" class="pa-0">
              <v-table density="compact" class="ml-8 bg-grey-lighten-5">
                <thead>
                  <tr>
                    <th>Client</th>
                    <th>Calls</th>
                    <th>Tokens</th>
                    <th>Cost</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="c in agencyClients" :key="String(c.clientId)">
                    <td>{{ c.clientName }}</td>
                    <td>{{ c.calls }}</td>
                    <td>{{ formatTokens(c.tokens) }}</td>
                    <td>${{ Number(c.costUsd).toFixed(2) }}</td>
                  </tr>
                  <tr v-if="!agencyClients.length">
                    <td colspan="4" class="text-center text-medium-emphasis">No data</td>
                  </tr>
                </tbody>
              </v-table>
            </td>
          </tr>
        </template>
      </v-data-table>
    </v-card>

    <!-- Log Table -->
    <v-card class="mt-4">
      <v-card-title class="d-flex align-center">
        Detailed Logs
        <v-spacer />
        <v-select
          v-model="logFilters.agencyId"
          :items="agencyFilterItems"
          label="Agency"
          density="compact"
          variant="outlined"
          hide-details
          clearable
          style="max-width: 200px"
          class="mr-2"
        />
        <v-select
          v-model="logFilters.module"
          :items="moduleFilterItems"
          label="Module"
          density="compact"
          variant="outlined"
          hide-details
          clearable
          style="max-width: 200px"
          class="mr-2"
        />
        <v-select
          v-model="logFilters.success"
          :items="statusFilterItems"
          label="Status"
          density="compact"
          variant="outlined"
          hide-details
          clearable
          style="max-width: 140px"
        />
      </v-card-title>
      <v-data-table-server
        v-model:page="logPage"
        :headers="logHeaders"
        :items="logs"
        :items-length="logTotalElements"
        :items-per-page="25"
        :loading="logsLoading"
        density="compact"
        @update:page="loadLogs"
        @update:items-per-page="(v: number) => { logPageSize = v; loadLogs() }"
        @click:row="(_e: Event, row: { item: LogRow }) => openLogDetail(row.item.id)"
      >
        <template #item.createdAt="{ value }">
          {{ relativeTime(value) }}
        </template>
        <template #item.costUsd="{ value }">
          ${{ Number(value).toFixed(4) }}
        </template>
        <template #item.totalTokens="{ item }">
          {{ item.promptTokens }}/{{ item.completionTokens }}
        </template>
        <template #item.success="{ value }">
          <v-chip :color="value ? 'success' : 'error'" size="x-small">
            {{ value ? 'OK' : 'FAIL' }}
          </v-chip>
        </template>
      </v-data-table-server>
    </v-card>

    <!-- Log Detail Dialog -->
    <v-dialog v-model="detailDialog" max-width="900">
      <v-card v-if="logDetail">
        <v-card-title class="d-flex align-center">
          Log Detail
          <v-spacer />
          <v-chip :color="logDetail.success ? 'success' : 'error'" size="small" class="mr-2">
            {{ logDetail.success ? 'Success' : 'Failed' }}
          </v-chip>
          <v-btn icon="mdi-close" variant="text" size="small" @click="detailDialog = false" />
        </v-card-title>
        <v-divider />
        <v-card-text>
          <v-row dense>
            <v-col cols="6" sm="3"><strong>Module:</strong> {{ logDetail.module }}</v-col>
            <v-col cols="6" sm="3"><strong>Model:</strong> {{ logDetail.model }}</v-col>
            <v-col cols="6" sm="3"><strong>Tokens:</strong> {{ logDetail.promptTokens }}/{{ logDetail.completionTokens }}</v-col>
            <v-col cols="6" sm="3"><strong>Cost:</strong> ${{ Number(logDetail.costUsd).toFixed(4) }}</v-col>
            <v-col cols="6" sm="3"><strong>Duration:</strong> {{ logDetail.durationMs }}ms</v-col>
            <v-col cols="6" sm="3"><strong>Agency:</strong> {{ logDetail.agencyName }}</v-col>
            <v-col cols="6" sm="3"><strong>Time:</strong> {{ new Date(logDetail.createdAt).toLocaleString() }}</v-col>
          </v-row>

          <div v-if="logDetail.errorMessage" class="mt-3">
            <strong class="text-error">Error:</strong>
            <pre class="error-box mt-1">{{ logDetail.errorMessage }}</pre>
          </div>

          <div class="mt-3">
            <strong>Input:</strong>
            <pre class="io-box mt-1">{{ logDetail.inputText || '(not stored)' }}</pre>
          </div>

          <div class="mt-3">
            <strong>Output:</strong>
            <pre class="io-box mt-1">{{ logDetail.outputText || '(not stored)' }}</pre>
          </div>
        </v-card-text>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ownerApi } from '@/api/owner'
import { Bar } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
  Tooltip,
  Legend,
} from 'chart.js'

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend)

/* ---- Types ---- */
interface ModuleRow { module: string; calls: number; tokens: number; costUsd: number }
interface AgencyRow { agencyId: string; agencyName: string; calls: number; tokens: number; costUsd: number }
interface DayRow { date: string; calls: number; tokens: number; costUsd: number }
interface ClientRow { clientId: string; clientName: string; calls: number; tokens: number; costUsd: number }

interface Summary {
  totalCalls: number; successfulCalls: number; failedCalls: number
  totalTokens: number; totalPromptTokens: number; totalCompletionTokens: number
  totalCostUsd: number; avgDurationMs: number
  byModule: ModuleRow[]; byAgency: AgencyRow[]; byDay: DayRow[]
}

interface LogRow {
  id: string; agencyId: string; agencyName: string; clientId: string
  module: string; model: string; promptTokens: number; completionTokens: number
  totalTokens: number; costUsd: number; durationMs: number; success: boolean
  errorMessage: string | null; createdAt: string
}

interface LogDetail extends LogRow {
  inputText: string | null; outputText: string | null
}

/* ---- State ---- */
const datePreset = ref('30d')
const fromDate = ref('')
const toDate = ref('')

const summary = ref<Summary>({
  totalCalls: 0, successfulCalls: 0, failedCalls: 0,
  totalTokens: 0, totalPromptTokens: 0, totalCompletionTokens: 0,
  totalCostUsd: 0, avgDurationMs: 0,
  byModule: [], byAgency: [], byDay: [],
})

const logs = ref<LogRow[]>([])
const logPage = ref(1)
const logPageSize = ref(25)
const logTotalElements = ref(0)
const logsLoading = ref(false)

const expandedAgency = ref<string | null>(null)
const agencyClients = ref<ClientRow[]>([])

const detailDialog = ref(false)
const logDetail = ref<LogDetail | null>(null)

const logFilters = reactive({ agencyId: null as string | null, module: null as string | null, success: null as boolean | null })

/* ---- Table headers ---- */
const agencyHeaders = [
  { title: 'Agency', key: 'agencyName' },
  { title: 'Calls', key: 'calls' },
  { title: 'Tokens', key: 'tokens' },
  { title: 'Cost', key: 'costUsd' },
  { title: '', key: 'actions', sortable: false, width: 40 },
]

const logHeaders = [
  { title: 'Time', key: 'createdAt', width: 140 },
  { title: 'Agency', key: 'agencyName' },
  { title: 'Module', key: 'module' },
  { title: 'Model', key: 'model' },
  { title: 'Tokens (in/out)', key: 'totalTokens', sortable: false },
  { title: 'Cost', key: 'costUsd', width: 90 },
  { title: 'Duration', key: 'durationMs', width: 90 },
  { title: 'Status', key: 'success', width: 80 },
]

/* ---- Filter items ---- */
const agencyFilterItems = computed(() =>
  summary.value.byAgency.map(a => ({ title: a.agencyName, value: a.agencyId }))
)
const moduleFilterItems = computed(() =>
  summary.value.byModule.map(m => ({ title: m.module, value: m.module }))
)
const statusFilterItems = [
  { title: 'Success', value: true },
  { title: 'Failed', value: false },
]

/* ---- Date helpers ---- */
function getDateRange(): { from?: string; to?: string } {
  if (datePreset.value === 'custom') {
    return { from: fromDate.value || undefined, to: toDate.value || undefined }
  }
  const now = new Date()
  const days = datePreset.value === 'today' ? 0 : datePreset.value === '7d' ? 7 : datePreset.value === '90d' ? 90 : 30
  const from = new Date(now)
  from.setDate(from.getDate() - days)
  return { from: from.toISOString().slice(0, 10), to: now.toISOString().slice(0, 10) }
}

function onPresetChange() {
  if (datePreset.value !== 'custom') loadAll()
}

/* ---- Data loading ---- */
async function loadSummary() {
  try {
    const { data } = await ownerApi.getAiAuditSummary(getDateRange())
    summary.value = data
  } catch (e) { console.error('Failed to load summary', e) }
}

async function loadLogs() {
  logsLoading.value = true
  try {
    const range = getDateRange()
    const params: Record<string, unknown> = {
      page: logPage.value - 1,
      size: logPageSize.value,
      ...range,
    }
    if (logFilters.agencyId) params.agencyId = logFilters.agencyId
    if (logFilters.module) params.module = logFilters.module
    if (logFilters.success !== null && logFilters.success !== undefined) params.success = logFilters.success
    const { data } = await ownerApi.getAiAuditLogs(params)
    logs.value = data.content
    logTotalElements.value = data.totalElements
  } catch (e) { console.error('Failed to load logs', e) }
  logsLoading.value = false
}

async function loadAll() {
  await Promise.all([loadSummary(), loadLogs()])
}

async function toggleAgencyExpand(item: AgencyRow) {
  if (expandedAgency.value === item.agencyId) {
    expandedAgency.value = null
    agencyClients.value = []
    return
  }
  expandedAgency.value = item.agencyId
  try {
    const { data } = await ownerApi.getAiAuditAgency(item.agencyId, getDateRange())
    agencyClients.value = data.byClient || []
  } catch (e) { console.error('Failed to load agency detail', e) }
}

async function openLogDetail(logId: string) {
  try {
    const { data } = await ownerApi.getAiAuditLogDetail(logId)
    logDetail.value = data
    detailDialog.value = true
  } catch (e) { console.error('Failed to load log detail', e) }
}

/* ---- Formatters ---- */
function formatTokens(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1) + 'M'
  if (n >= 1_000) return (n / 1_000).toFixed(1) + 'K'
  return String(n)
}

function relativeTime(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins}m ago`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

/* ---- Chart data ---- */
const barColors = ['#1976D2', '#42A5F5', '#90CAF9']

const costByDayData = computed(() => {
  const days = [...summary.value.byDay].reverse()
  return {
    labels: days.map(d => d.date),
    datasets: [{
      label: 'Cost ($)',
      data: days.map(d => Number(d.costUsd)),
      backgroundColor: barColors[0],
    }],
  }
})

const moduleColors = ['#1976D2', '#388E3C', '#F57C00', '#D32F2F', '#7B1FA2', '#00796B', '#5D4037', '#455A64']

const callsByModuleData = computed(() => ({
  labels: summary.value.byModule.map(m => m.module),
  datasets: [{
    label: 'Calls',
    data: summary.value.byModule.map(m => m.calls),
    backgroundColor: summary.value.byModule.map((_, i) => moduleColors[i % moduleColors.length]),
  }],
}))

const barOptions = {
  responsive: true, maintainAspectRatio: false,
  plugins: { legend: { display: false } },
  scales: { y: { beginAtZero: true } },
}

const horizontalBarOptions = {
  responsive: true, maintainAspectRatio: false,
  indexAxis: 'y' as const,
  plugins: { legend: { display: false } },
  scales: { x: { beginAtZero: true } },
}

/* ---- Watchers ---- */
watch(() => [logFilters.agencyId, logFilters.module, logFilters.success], () => {
  logPage.value = 1
  loadLogs()
})

/* ---- Init ---- */
onMounted(() => loadAll())
</script>

<style scoped>
.io-box, .error-box {
  max-height: 300px;
  overflow: auto;
  background: #f5f5f5;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  padding: 12px;
  font-family: 'Roboto Mono', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
.error-box {
  background: #ffebee;
  border-color: #ef9a9a;
}
</style>
