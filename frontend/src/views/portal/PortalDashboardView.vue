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
          <v-col cols="12" sm="4" md="3">
            <v-text-field
              v-model="dateFrom"
              label="From"
              type="date"
              variant="outlined"
              density="compact"
              hide-details
            />
          </v-col>
          <v-col cols="12" sm="4" md="3">
            <v-text-field
              v-model="dateTo"
              label="To"
              type="date"
              variant="outlined"
              density="compact"
              hide-details
            />
          </v-col>
          <v-col cols="12" sm="4" md="2">
            <v-btn color="primary" variant="tonal" @click="loadKpis" :loading="loadingKpis">
              <v-icon start>mdi-refresh</v-icon>
              Update
            </v-btn>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Loading -->
    <v-progress-linear v-if="loadingKpis" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- KPI Cards -->
    <v-row v-if="kpis">
      <v-col cols="12" sm="6" md="3" v-for="kpi in kpiCards" :key="kpi.label">
        <v-card variant="elevated" class="pa-4">
          <div class="d-flex align-center mb-2">
            <v-icon :color="kpi.color" size="24" class="mr-2">{{ kpi.icon }}</v-icon>
            <span class="text-body-2 text-medium-emphasis">{{ kpi.label }}</span>
          </div>
          <div class="text-h5 font-weight-bold">{{ kpi.value }}</div>
        </v-card>
      </v-col>
    </v-row>

    <!-- Empty state -->
    <v-alert v-if="!loadingKpis && !kpis && !error" type="info" variant="tonal" class="mt-4">
      No KPI data available for the selected period.
    </v-alert>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import portalApi from '@/api/portal'

interface KpiData {
  totalImpressions: number | null
  totalClicks: number | null
  totalSpend: number | null
  totalConversions: number | null
  avgCtr: number | null
  avgCpc: number | null
  avgRoas: number | null
}

interface ClientData {
  id: string
  name: string
  status: string
  industry: string
  timezone: string
  currency: string
}

const client = ref<ClientData | null>(null)
const kpis = ref<KpiData | null>(null)
const loadingKpis = ref(false)
const error = ref<string | null>(null)

// Default date range: last 30 days
const today = new Date()
const thirtyDaysAgo = new Date(today)
thirtyDaysAgo.setDate(today.getDate() - 30)
const dateTo = ref(today.toISOString().split('T')[0])
const dateFrom = ref(thirtyDaysAgo.toISOString().split('T')[0])

const kpiCards = computed(() => {
  if (!kpis.value) return []
  const k = kpis.value
  return [
    { label: 'Total Spend', value: formatCurrency(k.totalSpend), icon: 'mdi-currency-usd', color: 'blue' },
    { label: 'Impressions', value: formatNumber(k.totalImpressions), icon: 'mdi-eye', color: 'purple' },
    { label: 'Clicks', value: formatNumber(k.totalClicks), icon: 'mdi-cursor-default-click', color: 'teal' },
    { label: 'Conversions', value: formatDecimal(k.totalConversions), icon: 'mdi-cart-check', color: 'green' },
    { label: 'Avg. CTR', value: formatPercent(k.avgCtr), icon: 'mdi-percent', color: 'orange' },
    { label: 'Avg. CPC', value: formatCurrency(k.avgCpc), icon: 'mdi-cash', color: 'indigo' },
    { label: 'Avg. ROAS', value: formatDecimal(k.avgRoas), icon: 'mdi-trending-up', color: 'pink' },
  ]
})

function formatCurrency(val: number | null): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 2 }).format(val)
}

function formatNumber(val: number | null): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US').format(val)
}

function formatPercent(val: number | null): string {
  if (val == null) return '—'
  return (val * 100).toFixed(2) + '%'
}

function formatDecimal(val: number | null): string {
  if (val == null) return '—'
  return Number(val).toFixed(2)
}

async function loadClient() {
  try {
    const res = await portalApi.getMyClient()
    client.value = res.data
  } catch {
    // non-critical
  }
}

async function loadKpis() {
  loadingKpis.value = true
  error.value = null
  try {
    const res = await portalApi.getKpis({ from: dateFrom.value, to: dateTo.value })
    kpis.value = res.data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err.response?.data?.message || 'Failed to load KPI data'
    kpis.value = null
  } finally {
    loadingKpis.value = false
  }
}

onMounted(() => {
  loadClient()
  loadKpis()
})
</script>
