<template>
  <div>
    <!-- Header -->
    <div class="d-flex align-center mb-6">
      <div>
        <h1 class="text-h4 font-weight-bold">Dashboard</h1>
        <p class="text-body-1 text-medium-emphasis mt-1" v-if="client">
          {{ client.name }}
          <v-chip :color="client.status === 'ACTIVE' ? 'success' : 'warning'" size="small" class="ml-2">
            {{ client.status }}
          </v-chip>
        </p>
      </div>
    </div>

    <!-- Date Range Filter -->
    <v-card class="mb-6" variant="outlined">
      <v-card-text>
        <v-row align="center">
          <v-col cols="12" sm="3">
            <v-text-field
              v-model="dateFrom"
              label="From"
              type="date"
              variant="outlined"
              density="compact"
              hide-details
            />
          </v-col>
          <v-col cols="12" sm="3">
            <v-text-field
              v-model="dateTo"
              label="To"
              type="date"
              variant="outlined"
              density="compact"
              hide-details
            />
          </v-col>
          <v-col cols="12" sm="2">
            <v-btn color="primary" variant="tonal" @click="loadAll" :loading="loading">
              <v-icon start>mdi-refresh</v-icon>
              Update
            </v-btn>
          </v-col>
          <v-col cols="12" sm="4">
            <v-btn-group density="compact" variant="outlined">
              <v-btn @click="setRange(7)" size="small">7d</v-btn>
              <v-btn @click="setRange(14)" size="small">14d</v-btn>
              <v-btn @click="setRange(30)" size="small">30d</v-btn>
              <v-btn @click="setRange(90)" size="small">90d</v-btn>
            </v-btn-group>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Loading -->
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- KPI Cards with period comparison -->
    <v-row v-if="summary" class="mb-4">
      <v-col cols="6" sm="4" md="2" v-for="kpi in kpiCards" :key="kpi.key">
        <v-card variant="outlined" class="fill-height">
          <v-card-text class="text-center pa-3">
            <div class="d-flex align-center justify-center mb-1">
              <v-icon :color="kpi.color" size="18" class="mr-1">{{ kpi.icon }}</v-icon>
              <span class="text-caption text-medium-emphasis">{{ kpi.label }}</span>
            </div>
            <div class="text-h6 font-weight-bold">{{ kpi.value }}</div>
            <div v-if="kpi.change != null" class="text-caption" :class="kpi.changeColor">
              {{ kpi.change > 0 ? '↑' : '↓' }} {{ Math.abs(kpi.change).toFixed(1) }}%
            </div>
            <div v-else class="text-caption text-medium-emphasis">—</div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Daily Trend Charts -->
    <v-row class="mb-4" v-if="dailyData.length > 0">
      <v-col cols="12" md="6">
        <v-card>
          <v-card-title class="text-subtitle-1">Spend &amp; Conversions</v-card-title>
          <v-card-text>
            <Line :data="spendChartData" :options="spendChartOptions" style="height: 300px" />
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="6">
        <v-card>
          <v-card-title class="text-subtitle-1">Impressions &amp; Clicks</v-card-title>
          <v-card-text>
            <Line :data="trafficChartData" :options="trafficChartOptions" style="height: 300px" />
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <!-- Top Campaigns Table -->
    <v-card v-if="topCampaigns.length > 0">
      <v-card-title class="text-subtitle-1">Top Campaigns by Spend</v-card-title>
      <v-data-table
        :headers="topCampaignHeaders"
        :items="topCampaigns"
        density="compact"
        no-data-text="No campaign data for this period"
      >
        <template #item.status="{ item }">
          <v-chip :color="campaignStatusColor(item.status)" size="small">
            {{ item.status }}
          </v-chip>
        </template>
        <template #item.spend="{ item }">
          {{ formatCurrency(item.spend) }}
        </template>
        <template #item.conversions="{ item }">
          {{ formatDecimal(item.conversions) }}
        </template>
        <template #item.roas="{ item }">
          {{ formatRoas(item.roas) }}
        </template>
        <template #item.ctr="{ item }">
          {{ formatPercent(item.ctr) }}
        </template>
        <template #item.cpc="{ item }">
          {{ formatCurrency(item.cpc) }}
        </template>
      </v-data-table>
    </v-card>

    <!-- Empty state -->
    <v-alert v-if="!loading && !summary && !error" type="info" variant="tonal" class="mt-4">
      No KPI data available for the selected period.
    </v-alert>

    <!-- AI Chat Assistant -->
    <v-card class="mt-6">
      <v-card-title class="d-flex align-center">
        <v-icon color="purple" class="mr-2">mdi-robot-outline</v-icon>
        <span class="text-subtitle-1 font-weight-bold">AI Assistant</span>
      </v-card-title>
      <v-card-subtitle class="pb-0">
        Ask questions about your campaign performance — powered by AI.
      </v-card-subtitle>
      <v-card-text>
        <!-- Chat history -->
        <div
          v-if="chatHistory.length > 0"
          class="chat-history mb-4 pa-3 rounded-lg"
          style="max-height: 400px; overflow-y: auto; background: rgb(var(--v-theme-surface-variant))"
        >
          <div v-for="(msg, idx) in chatHistory" :key="idx" class="mb-3">
            <div v-if="msg.role === 'user'" class="d-flex justify-end">
              <v-chip color="primary" variant="flat" class="pa-3" style="white-space: normal; height: auto; max-width: 80%">
                {{ msg.text }}
              </v-chip>
            </div>
            <div v-else class="d-flex justify-start">
              <v-card variant="outlined" class="pa-3" style="max-width: 80%">
                <div class="text-body-2" style="white-space: pre-wrap">{{ msg.text }}</div>
                <div v-if="msg.tokens" class="text-caption text-medium-emphasis mt-1">
                  {{ msg.tokens }} tokens · ${{ msg.cost?.toFixed(4) }}
                </div>
              </v-card>
            </div>
          </div>
          <div v-if="chatLoading" class="d-flex justify-start">
            <v-card variant="outlined" class="pa-3">
              <v-progress-circular indeterminate size="20" width="2" color="purple" />
              <span class="ml-2 text-body-2 text-medium-emphasis">Thinking…</span>
            </v-card>
          </div>
        </div>

        <!-- Input -->
        <v-row no-gutters align="center">
          <v-col>
            <v-text-field
              v-model="chatQuestion"
              placeholder="Ask about your campaigns, spend, conversions…"
              variant="outlined"
              density="compact"
              hide-details
              :disabled="chatLoading"
              @keyup.enter="sendChat"
            />
          </v-col>
          <v-col cols="auto" class="ml-2">
            <v-btn
              color="purple"
              variant="flat"
              :loading="chatLoading"
              :disabled="!chatQuestion.trim()"
              @click="sendChat"
            >
              <v-icon>mdi-send</v-icon>
            </v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import portalApi from '@/api/portal'
import { Line } from 'vue-chartjs'
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js'

ChartJS.register(CategoryScale, LinearScale, PointElement, LineElement, Title, Tooltip, Legend, Filler)

interface ClientData {
  id: string
  name: string
  status: string
  industry: string
  timezone: string
  currency: string
}

const client = ref<ClientData | null>(null)
const summary = ref<any>(null)
const dailyData = ref<any[]>([])
const topCampaigns = ref<any[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// Default date range: last 30 days
const today = new Date()
const thirtyDaysAgo = new Date(today)
thirtyDaysAgo.setDate(today.getDate() - 30)
const dateTo = ref(today.toISOString().split('T')[0])
const dateFrom = ref(thirtyDaysAgo.toISOString().split('T')[0])

function setRange(days: number) {
  const now = new Date()
  const from = new Date(now)
  from.setDate(now.getDate() - days)
  dateFrom.value = from.toISOString().split('T')[0]
  dateTo.value = now.toISOString().split('T')[0]
  loadAll()
}

async function loadAll() {
  loading.value = true
  error.value = null
  try {
    const params = { from: dateFrom.value!, to: dateTo.value! }
    const [summaryRes, dailyRes, topRes] = await Promise.all([
      portalApi.getKpiSummary(params),
      portalApi.getKpiDaily(params),
      portalApi.getTopCampaigns({ ...params, limit: 10 }),
    ])
    summary.value = summaryRes.data
    dailyData.value = dailyRes.data
    topCampaigns.value = topRes.data
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load dashboard data'
  } finally {
    loading.value = false
  }
}

async function loadClient() {
  try {
    const res = await portalApi.getMyClient()
    client.value = res.data
  } catch {
    // non-critical
  }
}

onMounted(() => {
  loadClient()
  loadAll()
})

// ── AI Chat ──

interface ChatMessage {
  role: 'user' | 'assistant'
  text: string
  tokens?: number
  cost?: number
}

const chatQuestion = ref('')
const chatLoading = ref(false)
const chatHistory = ref<ChatMessage[]>([])

async function sendChat() {
  const question = chatQuestion.value.trim()
  if (!question || chatLoading.value) return

  chatHistory.value.push({ role: 'user', text: question })
  chatQuestion.value = ''
  chatLoading.value = true

  try {
    const res = await portalApi.aiChat(question)
    chatHistory.value.push({
      role: 'assistant',
      text: res.data.answer,
      tokens: res.data.tokensUsed,
      cost: res.data.cost,
    })
  } catch (e: any) {
    chatHistory.value.push({
      role: 'assistant',
      text: e.response?.data?.message || 'Sorry, something went wrong. Please try again.',
    })
  } finally {
    chatLoading.value = false
  }
}

// ── KPI Cards ──

interface KpiCard {
  key: string
  label: string
  value: string
  icon: string
  color: string
  change: number | null
  changeColor: string
}

const kpiCards = computed<KpiCard[]>(() => {
  if (!summary.value) return []
  const cur = summary.value.current || {}
  const chg = summary.value.changes || {}

  const upIsBad = new Set(['spend', 'cpc'])

  const items = [
    { key: 'spend', label: 'Spend', value: formatCurrency(cur.totalSpend), changeKey: 'spend', icon: 'mdi-currency-usd', color: 'blue' },
    { key: 'impressions', label: 'Impressions', value: formatNumber(cur.totalImpressions), changeKey: 'impressions', icon: 'mdi-eye', color: 'purple' },
    { key: 'clicks', label: 'Clicks', value: formatNumber(cur.totalClicks), changeKey: 'clicks', icon: 'mdi-cursor-default-click', color: 'teal' },
    { key: 'conversions', label: 'Conversions', value: formatDecimal(cur.totalConversions), changeKey: 'conversions', icon: 'mdi-cart-check', color: 'green' },
    { key: 'ctr', label: 'CTR', value: formatPercent(cur.avgCtr), changeKey: 'ctr', icon: 'mdi-percent', color: 'orange' },
    { key: 'cpc', label: 'CPC', value: formatCurrency(cur.avgCpc), changeKey: 'cpc', icon: 'mdi-cash', color: 'indigo' },
  ]

  return items.map((item) => {
    const change: number | null = chg[item.changeKey] ?? null
    let changeColor = 'text-medium-emphasis'
    if (change != null && change !== 0) {
      const isUp = change > 0
      const isGood = upIsBad.has(item.key) ? !isUp : isUp
      changeColor = isGood ? 'text-success' : 'text-error'
    }
    return { key: item.key, label: item.label, value: item.value, icon: item.icon, color: item.color, change, changeColor }
  })
})

// ── Chart: Spend & Conversions ──

function shortDate(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

const spendChartData = computed(() => ({
  labels: dailyData.value.map((d) => shortDate(d.date)),
  datasets: [
    {
      label: 'Spend ($)',
      data: dailyData.value.map((d) => d.spend),
      borderColor: '#1976D2',
      backgroundColor: 'rgba(25, 118, 210, 0.1)',
      fill: true,
      tension: 0.3,
      yAxisID: 'y',
    },
    {
      label: 'Conversions',
      data: dailyData.value.map((d) => d.conversions),
      borderColor: '#4CAF50',
      backgroundColor: 'rgba(76, 175, 80, 0.1)',
      fill: false,
      tension: 0.3,
      yAxisID: 'y1',
    },
  ],
}))

const spendChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: {
    legend: { position: 'top' as const },
    tooltip: {
      callbacks: {
        label: (ctx: any) => {
          const label = ctx.dataset.label || ''
          const val = ctx.parsed.y
          if (label.includes('Spend')) return `${label}: $${val.toFixed(2)}`
          return `${label}: ${val.toFixed(2)}`
        },
      },
    },
  },
  scales: {
    y: {
      type: 'linear' as const,
      display: true,
      position: 'left' as const,
      title: { display: true, text: 'Spend ($)' },
      grid: { drawOnChartArea: true },
    },
    y1: {
      type: 'linear' as const,
      display: true,
      position: 'right' as const,
      title: { display: true, text: 'Conversions' },
      grid: { drawOnChartArea: false },
    },
  },
}))

// ── Chart: Impressions & Clicks ──

const trafficChartData = computed(() => ({
  labels: dailyData.value.map((d) => shortDate(d.date)),
  datasets: [
    {
      label: 'Impressions',
      data: dailyData.value.map((d) => d.impressions),
      borderColor: '#9C27B0',
      backgroundColor: 'rgba(156, 39, 176, 0.1)',
      fill: false,
      tension: 0.3,
      yAxisID: 'y',
    },
    {
      label: 'Clicks',
      data: dailyData.value.map((d) => d.clicks),
      borderColor: '#009688',
      backgroundColor: 'rgba(0, 150, 136, 0.1)',
      fill: false,
      tension: 0.3,
      yAxisID: 'y1',
    },
  ],
}))

const trafficChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  interaction: { mode: 'index' as const, intersect: false },
  plugins: {
    legend: { position: 'top' as const },
    tooltip: {
      callbacks: {
        label: (ctx: any) => `${ctx.dataset.label}: ${formatNumber(ctx.parsed.y)}`,
      },
    },
  },
  scales: {
    y: {
      type: 'linear' as const,
      display: true,
      position: 'left' as const,
      title: { display: true, text: 'Impressions' },
      grid: { drawOnChartArea: true },
    },
    y1: {
      type: 'linear' as const,
      display: true,
      position: 'right' as const,
      title: { display: true, text: 'Clicks' },
      grid: { drawOnChartArea: false },
    },
  },
}))

// ── Top Campaigns Table ──

const topCampaignHeaders = [
  { title: 'Campaign Name', key: 'campaignName' },
  { title: 'Status', key: 'status', width: '120px' },
  { title: 'Spend', key: 'spend', align: 'end' as const },
  { title: 'Conversions', key: 'conversions', align: 'end' as const },
  { title: 'ROAS', key: 'roas', align: 'end' as const },
  { title: 'CTR', key: 'ctr', align: 'end' as const },
  { title: 'CPC', key: 'cpc', align: 'end' as const },
]

// ── Format helpers ──

function formatCurrency(val: any): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
  }).format(Number(val))
}

function formatNumber(val: any): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US').format(Number(val))
}

function formatPercent(val: any): string {
  if (val == null) return '—'
  return Number(val).toFixed(2) + '%'
}

function formatDecimal(val: any): string {
  if (val == null) return '—'
  return Number(val).toFixed(2)
}

function formatRoas(val: any): string {
  if (val == null) return '—'
  return `${Number(val).toFixed(2)}x`
}

function campaignStatusColor(status: string): string {
  const map: Record<string, string> = {
    PUBLISHED: 'success',
    ACTIVE: 'success',
    PAUSED: 'grey',
    DRAFT: 'grey',
    ARCHIVED: 'error',
  }
  return map[status] || 'grey'
}
</script>
