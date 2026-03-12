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
          @update:model-value="onClientChange"
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

    <v-row class="mb-4">
      <v-col cols="12">
        <div class="d-flex flex-wrap ga-2">
          <v-btn
            color="deep-purple"
            :loading="dashStore.budgetLoading"
            :disabled="!selectedClient"
            @click="loadBudgetAnalysis"
          >
            <v-icon start>mdi-chart-donut</v-icon>
            Budget Analysis
          </v-btn>
          <v-btn
            color="indigo"
            variant="outlined"
            :loading="dashStore.audienceLoading"
            :disabled="!selectedClient"
            @click="loadAudienceSuggestions"
          >
            <v-icon start>mdi-account-group-outline</v-icon>
            AI Audiences
          </v-btn>
        </div>
      </v-col>
    </v-row>

    <!-- Loading -->
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- Anomaly Alert Banner -->
    <v-alert
      v-if="dashStore.highAnomalyCount > 0"
      type="error"
      variant="tonal"
      prominent
      class="mb-4"
      closable
    >
      <div class="d-flex align-center">
        <v-icon class="mr-2" size="24">mdi-alert-circle</v-icon>
        <div>
          <strong>{{ dashStore.highAnomalyCount }} high-severity anomal{{ dashStore.highAnomalyCount === 1 ? 'y' : 'ies' }} detected!</strong>
          <div class="text-body-2" v-if="dashStore.anomalies?.details">
            {{ dashStore.anomalies.details.filter((d: any) => d.riskLevel === 'HIGH').map((d: any) => d.description).join(' · ') }}
          </div>
        </div>
        <v-spacer />
        <v-btn size="small" variant="outlined" color="error" to="/suggestions">
          View Suggestions
        </v-btn>
      </div>
    </v-alert>

    <!-- Medium Anomaly Info -->
    <v-alert
      v-if="dashStore.anomalies?.anomaliesDetected > 0 && dashStore.highAnomalyCount === 0"
      type="warning"
      variant="tonal"
      class="mb-4"
      density="compact"
      closable
    >
      <v-icon class="mr-1" size="16">mdi-alert</v-icon>
      {{ dashStore.anomalies.anomaliesDetected }} anomal{{ dashStore.anomalies.anomaliesDetected === 1 ? 'y' : 'ies' }} detected — check Suggestions for details.
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
            <v-card-title class="text-subtitle-1 d-flex flex-wrap align-center ga-2">
              <span>Chart 1</span>
              <v-spacer />
              <v-select
                v-model="primaryChartMetric"
                :items="chartMetricOptions"
                item-title="label"
                item-value="key"
                label="Primary metric"
                variant="outlined"
                density="compact"
                hide-details
                style="max-width: 180px"
              />
              <v-select
                v-model="secondaryChartMetric"
                :items="chartMetricOptions"
                item-title="label"
                item-value="key"
                label="Secondary metric"
                variant="outlined"
                density="compact"
                hide-details
                style="max-width: 180px"
              />
            </v-card-title>
            <v-card-text>
              <Line :data="primaryChartData" :options="primaryChartOptions" style="height: 300px" />
            </v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" md="6">
          <v-card>
            <v-card-title class="text-subtitle-1 d-flex flex-wrap align-center ga-2">
              <span>Chart 2</span>
              <v-spacer />
              <v-select
                v-model="tertiaryChartMetric"
                :items="chartMetricOptions"
                item-title="label"
                item-value="key"
                label="Primary metric"
                variant="outlined"
                density="compact"
                hide-details
                style="max-width: 180px"
              />
              <v-select
                v-model="quaternaryChartMetric"
                :items="chartMetricOptions"
                item-title="label"
                item-value="key"
                label="Secondary metric"
                variant="outlined"
                density="compact"
                hide-details
                style="max-width: 180px"
              />
            </v-card-title>
            <v-card-text>
              <Line :data="secondaryChartData" :options="secondaryChartOptions" style="height: 300px" />
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <v-card v-if="campaignRows.length > 0">
        <v-card-title class="d-flex align-center">
          <span>Campaigns</span>
          <v-chip v-if="selectedCampaignId" color="primary" size="small" class="ml-3">
            Filter: {{ selectedCampaignName }}
          </v-chip>
          <v-spacer />
          <v-btn v-if="selectedCampaignId" variant="text" color="primary" @click="selectCampaign(null)">
            <v-icon start>mdi-filter-off</v-icon>
            All campaigns
          </v-btn>
          <v-btn color="primary" variant="outlined" to="/campaigns">
            <v-icon start>mdi-lightbulb-on-outline</v-icon>
            View Suggestions
          </v-btn>
        </v-card-title>
        <v-data-table
          :headers="campaignTableHeaders"
          :items="campaignRows"
          density="compact"
          no-data-text="No campaign data for this period"
          item-value="id"
          hover
          @click:row="onCampaignRowClick"
        >
          <template #item.status="{ item }">
            <v-chip
              :color="selectedCampaignId === item.id ? 'primary' : campaignStatusColor(item.status)"
              size="small"
            >
              {{ item.status }}
            </v-chip>
          </template>
          <template #item.spend="{ item }">{{ formatCurrency(item.spend) }}</template>
          <template #item.clicks="{ item }">{{ formatNumber(item.clicks) }}</template>
          <template #item.conversions="{ item }">{{ formatTrimmedNumber(item.conversions) }}</template>
          <template #item.ctr="{ item }">{{ formatPercent(item.ctr) }}</template>
          <template #item.roas="{ item }">{{ formatRoas(item.roas) }}</template>
        </v-data-table>
      </v-card>

      <v-divider class="my-6" />

      <div class="d-flex align-center mb-4">
        <h2 class="text-h6">Budget Analysis</h2>
        <v-spacer />
        <v-chip v-if="budgetHasEnoughData" color="success" size="small" variant="tonal">Enough insight data</v-chip>
        <v-chip v-else color="warning" size="small" variant="tonal">Needs at least 7 days</v-chip>
      </div>

      <v-alert v-if="selectedClient && !budgetHasEnoughData" type="info" variant="tonal" class="mb-4">
        Budget analysis needs at least 7 days of insight data to be useful.
      </v-alert>

      <template v-if="dashStore.budgetAnalysis && !dashStore.budgetAnalysis.error">
        <v-card variant="tonal" color="deep-purple" class="mb-4">
          <v-card-title class="text-subtitle-1">
            <v-icon class="mr-2">mdi-robot</v-icon>AI Recommendation
          </v-card-title>
          <v-card-text class="text-body-2" style="white-space: pre-line">
            {{ dashStore.budgetAnalysis.narrative }}
          </v-card-text>
        </v-card>

        <v-row class="mb-4">
          <v-col cols="12" md="4">
            <v-card variant="outlined" class="h-100">
              <v-card-title class="text-subtitle-2">Pacing gauge</v-card-title>
              <v-card-text class="text-center">
                <v-progress-circular
                  :model-value="budgetPacingGauge"
                  :color="budgetPacingColor"
                  size="148"
                  width="12"
                >
                  {{ Math.round(budgetPacingGauge) }}%
                </v-progress-circular>
                <div class="text-body-1 font-weight-medium mt-4">
                  {{ Math.round(budgetSpentPct) }}% spent, {{ Math.round(monthElapsedPct) }}% of month
                </div>
                <div class="text-body-2 text-medium-emphasis mt-2">
                  {{ budgetPacingSummary }}
                </div>
              </v-card-text>
            </v-card>
          </v-col>

          <v-col cols="12" md="8">
            <v-card variant="outlined" class="h-100">
              <v-card-title class="text-subtitle-2">Day-of-week table</v-card-title>
              <v-card-text>
                <v-table density="compact">
                  <thead>
                    <tr>
                      <th>Day</th>
                      <th class="text-end">Avg Conversions</th>
                      <th class="text-end">Avg CPA</th>
                      <th class="text-end">Avg ROAS</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="d in budgetDaysOfWeek"
                      :key="d.day"
                      :class="{
                        'bg-green-lighten-5': d.day === dashStore.budgetAnalysis.dayOfWeek?.bestDay,
                        'bg-red-lighten-5': d.day === dashStore.budgetAnalysis.dayOfWeek?.worstDay,
                      }"
                    >
                      <td>{{ shortDayName(d.day) }}</td>
                      <td class="text-end">{{ formatTrimmedNumber(d.conversions) }}</td>
                      <td class="text-end">{{ formatCurrency(dayCpa(d)) }}</td>
                      <td class="text-end">{{ formatRoas(d.roas) }}</td>
                    </tr>
                  </tbody>
                </v-table>
                <div class="text-caption text-medium-emphasis mt-2">
                  {{ dashStore.budgetAnalysis.dayOfWeek?.recommendation }}
                </div>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>

        <v-card v-if="budgetCampaignRanking.length" variant="outlined" class="mb-4">
          <v-card-title class="text-subtitle-2">Campaign ranking</v-card-title>
          <v-data-table :headers="budgetRankHeaders" :items="budgetCampaignRanking" density="compact">
            <template #item.dailyBudget="{ item }">
              {{ formatCurrency(dashboardRow(item).dailyBudget) }}
            </template>
            <template #item.roas30d="{ item }">
              <span :class="rankingRoasClass(dashboardRow(item).roas30d)">{{ formatRoas(dashboardRow(item).roas30d) }}</span>
            </template>
            <template #item.status="{ item }">
              <v-chip :color="campaignStatusColor(dashboardRow(item).status)" size="x-small">{{ dashboardRow(item).status }}</v-chip>
            </template>
            <template #item.suggestion="{ item }">
              <v-chip :color="budgetRecommendationColor(dashboardRow(item).suggestion)" size="x-small">
                {{ dashboardRow(item).suggestion?.replace(/_/g, ' ') }}
              </v-chip>
            </template>
          </v-data-table>
        </v-card>
      </template>

      <v-alert v-else-if="dashStore.budgetAnalysis?.error" type="warning" variant="tonal" class="mb-6">
        {{ dashStore.budgetAnalysis.error }}
      </v-alert>

      <v-alert v-else type="info" variant="tonal" class="mb-6">
        No budget analysis yet. Click Budget Analysis to fetch it.
      </v-alert>

      <div class="d-flex align-center mb-4">
        <h2 class="text-h6">AI Audiences</h2>
      </div>

      <template v-if="dashStore.audienceSuggestions && !dashStore.audienceSuggestions.error">
        <v-card v-if="dashStore.audienceSuggestions.strategy_notes" variant="tonal" color="indigo" class="mb-4">
          <v-card-title class="text-subtitle-1">
            <v-icon class="mr-2">mdi-brain</v-icon>Strategy notes
          </v-card-title>
          <v-card-text>{{ dashStore.audienceSuggestions.strategy_notes }}</v-card-text>
        </v-card>

        <v-alert
          v-for="(warning, index) in audienceOverlapWarnings"
          :key="`overlap-${index}`"
          type="warning"
          variant="tonal"
          class="mb-2"
        >
          {{ warning }}
        </v-alert>

        <v-row v-if="recommendedAudiences.length" class="mb-4">
          <v-col v-for="(audience, index) in recommendedAudiences" :key="index" cols="12" md="6" xl="4">
            <v-card variant="outlined" class="h-100">
              <v-card-title class="d-flex align-center flex-wrap ga-2">
                <span>{{ audience.name || `Audience ${Number(index) + 1}` }}</span>
                <v-spacer />
                <v-chip size="x-small" :color="audienceTypeColor(audience.type)">{{ audience.type || 'UNKNOWN' }}</v-chip>
              </v-card-title>
              <v-card-text>
                <div class="text-body-2 mb-3">{{ audience.rationale || 'No rationale provided.' }}</div>
                <div class="d-flex flex-wrap ga-2 mb-3">
                  <v-chip size="x-small" variant="tonal">{{ audience.funnel_stage || 'Stage n/a' }}</v-chip>
                  <v-chip size="x-small" variant="tonal">Size: {{ audience.estimated_size || 'n/a' }}</v-chip>
                </div>
                <div class="text-caption mb-1">Confidence</div>
                <v-progress-linear
                  :model-value="audienceConfidenceValue(audience.confidence)"
                  :color="audienceConfidenceColor(audience.confidence)"
                  rounded
                  height="8"
                />
                <div class="text-caption text-medium-emphasis mt-1">{{ audience.confidence || 'UNKNOWN' }}</div>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>

        <v-card v-if="audienceExclusions.length" variant="outlined" class="mb-4">
          <v-card-title class="text-subtitle-2">Exclusion recommendations</v-card-title>
          <v-list lines="two">
            <v-list-item v-for="(item, index) in audienceExclusions" :key="index">
              <v-list-item-title>{{ item.description }}</v-list-item-title>
              <v-list-item-subtitle>{{ item.targeting_spec }}</v-list-item-subtitle>
            </v-list-item>
          </v-list>
        </v-card>

        <v-alert v-if="!recommendedAudiences.length && !audienceExclusions.length && !audienceOverlapWarnings.length" type="info" variant="tonal">
          No audience recommendations yet.
        </v-alert>
      </template>

      <v-alert v-else-if="dashStore.audienceSuggestions?.error" type="warning" variant="tonal">
        {{ dashStore.audienceSuggestions.error }}
      </v-alert>

      <v-alert v-else type="info" variant="tonal">
        No audience suggestions yet. Click AI Audiences to fetch them.
      </v-alert>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/api/client'
import { useDashboardStore } from '@/stores/dashboard'
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
const campaigns = ref<any[]>([])
const campaignMetrics = ref<Record<string, any>>({})
const summary = ref<any>(null)
const dailyData = ref<any[]>([])
const selectedCampaignId = ref<string | null>(null)
const currentInsights = ref<any[]>([])
const previousInsights = ref<any[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

// Default: last 30 days
const today = new Date()
const thirtyDaysAgo = new Date(today)
thirtyDaysAgo.setDate(today.getDate() - 30)
const dateFrom = ref(thirtyDaysAgo.toISOString().split('T')[0])
const dateTo = ref(today.toISOString().split('T')[0])
const primaryChartMetric = ref('spend')
const secondaryChartMetric = ref('conversions')
const tertiaryChartMetric = ref('impressions')
const quaternaryChartMetric = ref('clicks')

// ── Data loading ──

function setRange(days: number) {
  const now = new Date()
  const from = new Date(now)
  from.setDate(now.getDate() - days)
  dateFrom.value = from.toISOString().split('T')[0]
  dateTo.value = now.toISOString().split('T')[0]
  loadDashboard()
}

function onClientChange() {
  selectedCampaignId.value = null
  currentInsights.value = []
  previousInsights.value = []
  dashStore.budgetAnalysis = null
  dashStore.audienceSuggestions = null
  loadDashboard()
}

async function loadDashboard() {
  if (!selectedClient.value) return
  loading.value = true
  error.value = null
  try {
    const params = { from: dateFrom.value, to: dateTo.value }
    const [summaryRes, dailyRes, campaignsRes] = await Promise.all([
      api.get(`/clients/${selectedClient.value}/kpis/summary`, { params }),
      api.get(`/clients/${selectedClient.value}/kpis/daily`, { params }),
      api.get(`/clients/${selectedClient.value}/campaigns`),
    ])
    campaigns.value = campaignsRes.data || []
    await loadCampaignMetrics()
    if (selectedCampaignId.value && !campaigns.value.some((campaign: any) => campaign.id === selectedCampaignId.value)) {
      selectedCampaignId.value = null
    }

    if (selectedCampaignId.value) {
      await loadSelectedCampaignMetrics()
    } else {
      summary.value = summaryRes.data
      dailyData.value = dailyRes.data
    }

    // Auto-run anomaly detection in background (non-blocking)
    dashStore.runAnomalyCheck(selectedClient.value).catch(() => {})
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load dashboard data'
  } finally {
    loading.value = false
  }
}

async function loadCampaignMetrics() {
  if (!campaigns.value.length) {
    campaignMetrics.value = {}
    return
  }

  const metricsEntries = await Promise.all(campaigns.value.map(async (campaign: any) => {
    try {
      const { data } = await api.get(`/campaigns/${campaign.id}/insights`, {
        params: { from: dateFrom.value, to: dateTo.value },
      })
      const aggregate = aggregateInsightRows(data || [])
      return [campaign.id, aggregate] as const
    } catch {
      return [campaign.id, {
        totalSpend: 0,
        totalImpressions: 0,
        totalClicks: 0,
        totalConversions: 0,
        avgCtr: 0,
        avgRoas: 0,
      }] as const
    }
  }))

  campaignMetrics.value = Object.fromEntries(metricsEntries)
}

async function loadSelectedCampaignMetrics() {
  if (!selectedCampaignId.value || !selectedClient.value) return

  const fromDate = new Date(dateFrom.value + 'T00:00:00')
  const toDate = new Date(dateTo.value + 'T00:00:00')
  const diffDays = Math.max(1, Math.round((toDate.getTime() - fromDate.getTime()) / 86400000))
  const prevToDate = new Date(fromDate)
  prevToDate.setDate(prevToDate.getDate() - 1)
  const prevFromDate = new Date(fromDate)
  prevFromDate.setDate(prevFromDate.getDate() - diffDays)

  const prevFrom = prevFromDate.toISOString().split('T')[0] ?? ''
  const prevTo = prevToDate.toISOString().split('T')[0] ?? ''

  const [currentRes, previousRes] = await Promise.all([
    api.get(`/campaigns/${selectedCampaignId.value}/insights`, { params: { from: dateFrom.value, to: dateTo.value } }),
    api.get(`/campaigns/${selectedCampaignId.value}/insights`, { params: { from: prevFrom, to: prevTo } }),
  ])

  currentInsights.value = currentRes.data || []
  previousInsights.value = previousRes.data || []
  summary.value = buildCampaignSummary(
    currentInsights.value,
    previousInsights.value,
    dateFrom.value ?? '',
    dateTo.value ?? '',
    prevFrom,
    prevTo,
  )
  dailyData.value = buildDailyData(currentInsights.value)
}

async function selectCampaign(campaignId: string | null) {
  selectedCampaignId.value = campaignId
  if (!selectedClient.value) return

  if (campaignId) {
    loading.value = true
    try {
      await loadSelectedCampaignMetrics()
    } catch (e: any) {
      error.value = e.response?.data?.message || 'Failed to load campaign metrics'
    } finally {
      loading.value = false
    }
  } else {
    await loadDashboard()
  }
}

const dashStore = useDashboardStore()

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

const sortedCampaigns = computed(() => [...campaigns.value].sort((a, b) => {
  const statusWeight = (status: string) => status === 'PUBLISHED' ? 0 : status === 'ACTIVE' ? 0 : status === 'PAUSED' ? 1 : 2
  const diff = statusWeight(a.status) - statusWeight(b.status)
  if (diff !== 0) return diff
  return a.name.localeCompare(b.name)
}))

const selectedCampaignName = computed(() =>
  sortedCampaigns.value.find((campaign: any) => campaign.id === selectedCampaignId.value)?.name || ''
)

const campaignRows = computed(() => sortedCampaigns.value.map((campaign: any) => ({
  id: campaign.id,
  name: campaign.name,
  status: campaign.status,
  spend: campaignMetrics.value[campaign.id]?.totalSpend ?? 0,
  clicks: campaignMetrics.value[campaign.id]?.totalClicks ?? 0,
  conversions: campaignMetrics.value[campaign.id]?.totalConversions ?? 0,
  ctr: campaignMetrics.value[campaign.id]?.avgCtr ?? 0,
  roas: campaignMetrics.value[campaign.id]?.avgRoas ?? 0,
})))

function dashboardRow(item: unknown): any {
  if (item && typeof item === 'object' && 'raw' in item) {
    return (item as { raw: any }).raw
  }
  return item as any
}

function onCampaignRowClick(_event: unknown, row: unknown) {
  const candidate = row && typeof row === 'object' && 'item' in row
    ? (row as { item: unknown }).item
    : null
  const selectedRow = candidate ? dashboardRow(candidate) : null
  if (selectedRow?.id) {
    selectCampaign(selectedRow.id)
  }
}

const campaignTableHeaders = [
  { title: 'Campaign', key: 'name' },
  { title: 'Status', key: 'status' },
  { title: 'Spend', key: 'spend', align: 'end' as const },
  { title: 'Clicks', key: 'clicks', align: 'end' as const },
  { title: 'Results', key: 'conversions', align: 'end' as const },
  { title: 'CTR', key: 'ctr', align: 'end' as const },
  { title: 'ROAS', key: 'roas', align: 'end' as const },
]
const budgetRankHeaders = [
  { title: 'Campaign', key: 'campaignName' },
  { title: 'Daily Budget', key: 'dailyBudget', align: 'end' as const },
  { title: 'ROAS', key: 'roas30d', align: 'end' as const },
  { title: 'Status', key: 'status' },
  { title: 'Recommendation', key: 'suggestion' },
]

const budgetHasEnoughData = computed(() => dailyData.value.length >= 7)
const monthElapsedPct = computed(() => Number(dashStore.budgetAnalysis?.pacing?.pctMonthElapsed || 0))
const budgetSpentPct = computed(() => {
  const current = Number(dashStore.budgetAnalysis?.pacing?.currentMonthSpend || 0)
  const projected = Number(dashStore.budgetAnalysis?.pacing?.projectedMonthSpend || 0)
  return projected > 0 ? (current / projected) * 100 : 0
})
const budgetPacingGauge = computed(() => Math.min(100, Math.max(0, budgetSpentPct.value)))
const budgetPacingColor = computed(() => {
  const status = dashStore.budgetAnalysis?.pacing?.pacingStatus
  if (status === 'ON_TRACK') return 'success'
  if (status === 'UNDERSPENDING') return 'warning'
  return 'error'
})
const budgetPacingSummary = computed(() => {
  const diff = budgetSpentPct.value - monthElapsedPct.value
  if (Math.abs(diff) < 10) return 'On track with current monthly pacing.'
  if (diff > 0) return 'Slightly ahead of pace.'
  return 'Behind pace and likely underspending.'
})
const budgetDaysOfWeek = computed(() => dashStore.budgetAnalysis?.dayOfWeek?.days || [])
const budgetCampaignRanking = computed(() => [...(dashStore.budgetAnalysis?.campaignRanking || [])]
  .sort((a: any, b: any) => Number(b.roas30d || 0) - Number(a.roas30d || 0)))
const recommendedAudiences = computed(() => dashStore.audienceSuggestions?.recommended_audiences || [])
const audienceExclusions = computed(() => dashStore.audienceSuggestions?.exclusion_recommendations || [])
const audienceOverlapWarnings = computed(() => dashStore.audienceSuggestions?.overlap_warnings || [])

const metricConfig: Record<string, { label: string; color: string; format: 'currency' | 'number' | 'percent' | 'roas' }> = {
  spend: { label: 'Spend', color: '#1976D2', format: 'currency' },
  conversions: { label: 'Results', color: '#4CAF50', format: 'number' },
  impressions: { label: 'Impressions', color: '#9C27B0', format: 'number' },
  clicks: { label: 'Clicks', color: '#009688', format: 'number' },
  ctr: { label: 'CTR', color: '#FB8C00', format: 'percent' },
  cpc: { label: 'CPC', color: '#3949AB', format: 'currency' },
  cpm: { label: 'CPM', color: '#5E35B1', format: 'currency' },
  roas: { label: 'ROAS', color: '#00897B', format: 'roas' },
  reach: { label: 'Reach', color: '#6D4C41', format: 'number' },
  frequency: { label: 'Frequency', color: '#546E7A', format: 'number' },
}

function shortDayName(day: string) {
  return day ? day.substring(0, 3) : '—'
}

function dayCpa(day: any) {
  const spend = Number(day?.spend || 0)
  const conversions = Number(day?.conversions || 0)
  return conversions > 0 ? spend / conversions : 0
}

function rankingRoasClass(roas: number) {
  if (Number(roas) >= 3) return 'text-success font-weight-bold'
  if (Number(roas) < 1) return 'text-error font-weight-bold'
  return 'text-medium-emphasis'
}

function budgetRecommendationColor(suggestion: string) {
  if (suggestion === 'INCREASE_BUDGET') return 'success'
  if (suggestion === 'DECREASE_BUDGET') return 'warning'
  if (suggestion === 'PAUSE_OR_RESTRUCTURE') return 'error'
  return 'grey'
}

function audienceTypeColor(type: string) {
  const value = String(type || '').toLowerCase()
  if (value.includes('lookalike')) return 'deep-purple'
  if (value.includes('retarget')) return 'warning'
  if (value.includes('interest')) return 'primary'
  return 'grey'
}

function audienceConfidenceValue(confidence: string) {
  const map: Record<string, number> = { HIGH: 85, MEDIUM: 60, LOW: 35 }
  return map[String(confidence || '').toUpperCase()] || 40
}

function audienceConfidenceColor(confidence: string) {
  const value = String(confidence || '').toUpperCase()
  if (value === 'HIGH') return 'success'
  if (value === 'MEDIUM') return 'warning'
  return 'error'
}

async function loadBudgetAnalysis() {
  if (!selectedClient.value) return
  await dashStore.fetchBudgetAnalysis(selectedClient.value)
}

async function loadAudienceSuggestions() {
  if (!selectedClient.value) return
  await dashStore.fetchAudienceSuggestions(selectedClient.value)
}

const chartMetricOptions = computed(() => Object.entries(metricConfig).map(([key, value]) => ({
  key,
  label: value.label,
})))

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
  const upIsBad = new Set(['spend', 'cpa'])

  const currentCpa = Number(cur.totalConversions) > 0 ? Number(cur.totalSpend) / Number(cur.totalConversions) : 0

  const items: { key: string; label: string; value: string; changeKey: string }[] = [
    { key: 'spend', label: 'Ad Spend', value: formatCurrency(cur.totalSpend), changeKey: 'spend' },
    { key: 'results', label: 'Results', value: formatTrimmedNumber(cur.totalConversions), changeKey: 'conversions' },
    { key: 'cpa', label: 'CPA', value: formatCurrency(currentCpa), changeKey: 'cpa' },
    { key: 'ctr', label: 'CTR', value: formatPercent(cur.avgCtr), changeKey: 'ctr' },
    { key: 'roas', label: 'ROAS', value: formatRoas(cur.avgRoas), changeKey: 'roas' },
    { key: 'clicks', label: 'Clicks', value: formatNumber(cur.totalClicks), changeKey: 'clicks' },
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

// ── Chart helpers ──

function shortDate(dateStr: string): string {
  const d = new Date(dateStr + 'T00:00:00')
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })
}

function metricLabel(metricKey: string) {
  return metricConfig[metricKey]?.label || metricKey
}

function metricFormatter(metricKey: string, value: number) {
  const config = metricConfig[metricKey]
  if (!config) return formatTrimmedNumber(value)
  if (config.format === 'currency') return formatCurrency(value)
  if (config.format === 'percent') return formatPercent(value)
  if (config.format === 'roas') return formatRoas(value)
  return formatTrimmedNumber(value)
}

function createChartData(primaryMetric: string, secondaryMetric: string) {
  return {
    labels: dailyData.value.map((d) => shortDate(d.date)),
    datasets: [
      {
        label: metricLabel(primaryMetric),
        data: dailyData.value.map((d) => Number(d[primaryMetric] || 0)),
        borderColor: metricConfig[primaryMetric]?.color || '#1976D2',
        backgroundColor: `${metricConfig[primaryMetric]?.color || '#1976D2'}1A`,
        fill: true,
        tension: 0.3,
        yAxisID: 'y',
      },
      {
        label: metricLabel(secondaryMetric),
        data: dailyData.value.map((d) => Number(d[secondaryMetric] || 0)),
        borderColor: metricConfig[secondaryMetric]?.color || '#4CAF50',
        backgroundColor: `${metricConfig[secondaryMetric]?.color || '#4CAF50'}1A`,
        fill: false,
        tension: 0.3,
        yAxisID: 'y1',
      },
    ],
  }
}

function createChartOptions(primaryMetric: string, secondaryMetric: string) {
  return {
    responsive: true,
    maintainAspectRatio: false,
    interaction: { mode: 'index' as const, intersect: false },
    plugins: {
      legend: { position: 'top' as const },
      tooltip: {
        callbacks: {
          label: (ctx: any) => {
            const metricKey = ctx.datasetIndex === 0 ? primaryMetric : secondaryMetric
            return `${ctx.dataset.label}: ${metricFormatter(metricKey, ctx.parsed.y)}`
          },
        },
      },
    },
    scales: {
      y: {
        type: 'linear' as const,
        display: true,
        position: 'left' as const,
        title: { display: true, text: metricLabel(primaryMetric) },
        grid: { drawOnChartArea: true },
      },
      y1: {
        type: 'linear' as const,
        display: true,
        position: 'right' as const,
        title: { display: true, text: metricLabel(secondaryMetric) },
        grid: { drawOnChartArea: false },
      },
    },
  }
}

const primaryChartData = computed(() => createChartData(primaryChartMetric.value, secondaryChartMetric.value))
const primaryChartOptions = computed(() => createChartOptions(primaryChartMetric.value, secondaryChartMetric.value))
const secondaryChartData = computed(() => createChartData(tertiaryChartMetric.value, quaternaryChartMetric.value))
const secondaryChartOptions = computed(() => createChartOptions(tertiaryChartMetric.value, quaternaryChartMetric.value))

// ── Aggregated daily charts ──

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
          if (label.includes('Spend')) return `${label}: ${formatCurrency(val)}`
          return `${label}: ${formatTrimmedNumber(val)}`
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

// ── Format helpers ──

function formatCurrency(val: any): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
    minimumFractionDigits: 0,
  }).format(Number(val))
}

function formatNumber(val: any): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US').format(Number(val))
}

function formatPercent(val: any): string {
  if (val == null) return '—'
  return formatTrimmedNumber(val) + '%'
}

function formatTrimmedNumber(val: any): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(Number(val))
}

function formatRoas(val: any): string {
  if (val == null) return '—'
  return `${formatTrimmedNumber(val)}x`
}

function calculatePctChange(current: number, previous: number): number | null {
  if (!previous) return null
  return ((current - previous) / previous) * 100
}

function aggregateInsightRows(rows: any[]) {
  const totals = rows.reduce((acc, item) => {
    acc.spend += Number(item.spend || 0)
    acc.impressions += Number(item.impressions || 0)
    acc.clicks += Number(item.clicks || 0)
    acc.conversions += Number(item.conversions || 0)
    acc.conversionValue += Number(item.conversionValue || 0)
    return acc
  }, { spend: 0, impressions: 0, clicks: 0, conversions: 0, conversionValue: 0 })

  const avgCtr = totals.impressions > 0 ? (totals.clicks / totals.impressions) * 100 : 0
  const avgRoas = totals.spend > 0 ? totals.conversionValue / totals.spend : 0

  return {
    totalSpend: totals.spend,
    totalImpressions: totals.impressions,
    totalClicks: totals.clicks,
    totalConversions: totals.conversions,
    avgCtr,
    avgRoas,
  }
}

function buildCampaignSummary(currentRows: any[], previousRows: any[], from: string, to: string, prevFrom: string, prevTo: string) {
  const current = aggregateInsightRows(currentRows)
  const previous = aggregateInsightRows(previousRows)
  const currentCpa = current.totalConversions > 0 ? current.totalSpend / current.totalConversions : 0
  const previousCpa = previous.totalConversions > 0 ? previous.totalSpend / previous.totalConversions : 0

  return {
    period: { from, to },
    previousPeriod: { from: prevFrom, to: prevTo },
    current,
    previous,
    changes: {
      spend: calculatePctChange(current.totalSpend, previous.totalSpend),
      clicks: calculatePctChange(current.totalClicks, previous.totalClicks),
      conversions: calculatePctChange(current.totalConversions, previous.totalConversions),
      ctr: calculatePctChange(current.avgCtr, previous.avgCtr),
      roas: calculatePctChange(current.avgRoas, previous.avgRoas),
      cpa: calculatePctChange(currentCpa, previousCpa),
    },
  }
}

function buildDailyData(rows: any[]) {
  const byDate = rows.reduce((acc: Record<string, any>, item) => {
    const date = item.date
    if (!acc[date]) {
      acc[date] = {
        date,
        spend: 0,
        impressions: 0,
        clicks: 0,
        conversions: 0,
        conversionValue: 0,
        reach: 0,
      }
    }
    acc[date].spend += Number(item.spend || 0)
    acc[date].impressions += Number(item.impressions || 0)
    acc[date].clicks += Number(item.clicks || 0)
    acc[date].conversions += Number(item.conversions || 0)
    acc[date].conversionValue += Number(item.conversionValue || 0)
    acc[date].reach += Number(item.reach || 0)
    return acc
  }, {})

  return Object.values(byDate)
    .map((item: any) => ({
      ...item,
      ctr: item.impressions > 0 ? (item.clicks / item.impressions) * 100 : 0,
      cpc: item.clicks > 0 ? item.spend / item.clicks : 0,
      cpm: item.impressions > 0 ? (item.spend * 1000) / item.impressions : 0,
      roas: item.spend > 0 ? item.conversionValue / item.spend : 0,
      frequency: item.reach > 0 ? item.impressions / item.reach : 0,
    }))
    .sort((a: any, b: any) => a.date.localeCompare(b.date))
}

function campaignStatusColor(status: string) {
  const map: Record<string, string> = { PUBLISHED: 'success', ACTIVE: 'success', PAUSED: 'warning', DRAFT: 'grey', ARCHIVED: 'error' }
  return map[status] || 'grey'
}
</script>
