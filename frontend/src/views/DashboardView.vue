<template>
  <div>
    <h1 class="text-h4 font-weight-bold mb-4">Dashboard</h1>

    <!-- ── Header: Client selector + Date range ── -->
    <v-row align="center" class="mb-4">
      <v-col cols="12" sm="4">
        <v-select
          v-model="selectedClient"
          :items="clients"
          item-title="name"
          item-value="id"
          label="Select Client"
          variant="outlined"
          density="compact"
          hide-details
          @update:model-value="loadDashboard"
        />
      </v-col>
      <v-col cols="12" sm="3">
        <v-text-field
          v-model="dateFrom"
          label="From"
          type="date"
          variant="outlined"
          density="compact"
          hide-details
          @change="loadDashboard"
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
          @change="loadDashboard"
        />
      </v-col>
      <v-col cols="12" sm="2">
        <v-btn-group density="compact" variant="outlined">
          <v-btn @click="setRange(7)" size="small">7d</v-btn>
          <v-btn @click="setRange(14)" size="small">14d</v-btn>
          <v-btn @click="setRange(30)" size="small">30d</v-btn>
          <v-btn @click="setRange(90)" size="small">90d</v-btn>
        </v-btn-group>
      </v-col>
    </v-row>

    <!-- Loading -->
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- No client selected -->
    <v-alert v-if="!selectedClient && !loading" type="info" variant="tonal" class="mb-4">
      Select a client to view dashboard data.
    </v-alert>

    <!-- No data for period -->
    <v-alert
      v-if="selectedClient && !loading && !error && summary && !hasData"
      type="info"
      variant="tonal"
      class="mb-4"
    >
      No data for this period. Try a different date range or run a sync first.
    </v-alert>

    <template v-if="selectedClient && summary">
      <!-- ── KPI Summary Cards with period comparison ── -->
      <v-row class="mb-4">
        <v-col v-for="kpi in kpiCards" :key="kpi.key" cols="6" sm="4" md="2">
          <v-card variant="outlined" class="fill-height">
            <v-card-text class="text-center pa-3">
              <div class="text-caption text-medium-emphasis mb-1">{{ kpi.label }}</div>
              <div class="text-h6 font-weight-bold">{{ kpi.value }}</div>
              <div v-if="kpi.change != null" class="text-caption" :class="kpi.changeColor">
                {{ kpi.change > 0 ? '↑' : '↓' }} {{ Math.abs(kpi.change).toFixed(1) }}%
              </div>
              <div v-else class="text-caption text-medium-emphasis">—</div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <!-- ── Daily Trend Charts ── -->
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

      <!-- ── Top Campaigns Table ── -->
      <v-card v-if="topCampaigns.length > 0">
        <v-card-title class="text-subtitle-1">Top Campaigns by Spend</v-card-title>
        <v-data-table
          :headers="topCampaignHeaders"
          :items="topCampaigns"
          density="compact"
          no-data-text="No campaign data for this period"
        >
          <template #item.spend="{ item }">
            {{ formatCurrency(item.spend) }}
          </template>
          <template #item.impressions="{ item }">
            {{ formatNumber(item.impressions) }}
          </template>
          <template #item.clicks="{ item }">
            {{ formatNumber(item.clicks) }}
          </template>
          <template #item.conversions="{ item }">
            {{ formatDecimal(item.conversions) }}
          </template>
          <template #item.ctr="{ item }">
            {{ formatPercent(item.ctr) }}
          </template>
          <template #item.cpc="{ item }">
            {{ formatCurrency(item.cpc) }}
          </template>
        </v-data-table>
      </v-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/api/client'
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

// ── Reactive state ──

const selectedClient = ref<string | null>(null)
const clients = ref<any[]>([])
const summary = ref<any>(null)
const dailyData = ref<any[]>([])
const topCampaigns = ref<any[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// Default: last 30 days
const today = new Date()
const thirtyDaysAgo = new Date(today)
thirtyDaysAgo.setDate(today.getDate() - 30)
const dateFrom = ref(thirtyDaysAgo.toISOString().split('T')[0])
const dateTo = ref(today.toISOString().split('T')[0])

// ── Data loading ──

function setRange(days: number) {
  const now = new Date()
  const from = new Date(now)
  from.setDate(now.getDate() - days)
  dateFrom.value = from.toISOString().split('T')[0]
  dateTo.value = now.toISOString().split('T')[0]
  loadDashboard()
}

async function loadDashboard() {
  if (!selectedClient.value) return
  loading.value = true
  error.value = null
  try {
    const params = { from: dateFrom.value, to: dateTo.value }
    const [summaryRes, dailyRes, topRes] = await Promise.all([
      api.get(`/clients/${selectedClient.value}/kpis/summary`, { params }),
      api.get(`/clients/${selectedClient.value}/kpis/daily`, { params }),
      api.get(`/clients/${selectedClient.value}/kpis/top-campaigns`, {
        params: { ...params, limit: 10 },
      }),
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

onMounted(async () => {
  try {
    const { data } = await api.get('/clients')
    clients.value = data
    if (data.length > 0) {
      selectedClient.value = data[0].id
      loadDashboard()
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load clients'
  }
})

// ── Computed: has any data? ──

const hasData = computed(() => {
  if (!summary.value?.current) return false
  const c = summary.value.current
  return c.totalSpend > 0 || c.totalImpressions > 0 || c.totalClicks > 0
})

// ── KPI Cards ──

interface KpiCard {
  key: string
  label: string
  value: string
  change: number | null
  changeColor: string
}

const kpiCards = computed<KpiCard[]>(() => {
  if (!summary.value) return []
  const cur = summary.value.current || {}
  const chg = summary.value.changes || {}

  // For spend & CPC: up is bad (red), down is good (green)
  // For everything else: up is good (green), down is bad (red)
  const upIsBad = new Set(['spend', 'cpc'])

  const items: { key: string; label: string; value: string; changeKey: string }[] = [
    { key: 'spend', label: 'Spend', value: formatCurrency(cur.totalSpend), changeKey: 'spend' },
    { key: 'impressions', label: 'Impressions', value: formatNumber(cur.totalImpressions), changeKey: 'impressions' },
    { key: 'clicks', label: 'Clicks', value: formatNumber(cur.totalClicks), changeKey: 'clicks' },
    { key: 'conversions', label: 'Conversions', value: formatDecimal(cur.totalConversions), changeKey: 'conversions' },
    { key: 'ctr', label: 'CTR', value: formatPercent(cur.avgCtr), changeKey: 'ctr' },
    { key: 'cpc', label: 'CPC', value: formatCurrency(cur.avgCpc), changeKey: 'cpc' },
  ]

  return items.map((item) => {
    const change: number | null = chg[item.changeKey] ?? null
    let changeColor = 'text-medium-emphasis'
    if (change != null && change !== 0) {
      const isUp = change > 0
      const isGood = upIsBad.has(item.key) ? !isUp : isUp
      changeColor = isGood ? 'text-success' : 'text-error'
    }
    return { key: item.key, label: item.label, value: item.value, change, changeColor }
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
  { title: 'Type', key: 'entityType', width: '80px' },
  { title: 'Entity ID', key: 'entityId' },
  { title: 'Spend', key: 'spend', align: 'end' as const },
  { title: 'Impressions', key: 'impressions', align: 'end' as const },
  { title: 'Clicks', key: 'clicks', align: 'end' as const },
  { title: 'Conversions', key: 'conversions', align: 'end' as const },
  { title: 'CTR %', key: 'ctr', align: 'end' as const },
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
</script>
