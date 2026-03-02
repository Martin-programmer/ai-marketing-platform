<template>
  <div>
    <h1 class="mb-4">Dashboard</h1>

    <v-select
      v-model="selectedClient"
      :items="store.clients"
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

    <v-alert v-if="!selectedClient" type="info" class="mb-4">
      Select a client to view dashboard data.
    </v-alert>

    <template v-if="selectedClient">
      <!-- KPI Cards -->
      <v-row class="mb-4">
        <v-col cols="12" sm="6" md="3">
          <v-card color="primary" variant="tonal">
            <v-card-text class="text-center">
              <div class="text-h5">{{ formatNumber(store.kpis?.totalSpend) }}</div>
              <div class="text-caption">Total Spend</div>
            </v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" sm="6" md="3">
          <v-card color="info" variant="tonal">
            <v-card-text class="text-center">
              <div class="text-h5">{{ formatNumber(store.kpis?.totalImpressions) }}</div>
              <div class="text-caption">Impressions</div>
            </v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" sm="6" md="3">
          <v-card color="success" variant="tonal">
            <v-card-text class="text-center">
              <div class="text-h5">{{ formatNumber(store.kpis?.totalClicks) }}</div>
              <div class="text-caption">Clicks</div>
            </v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" sm="6" md="3">
          <v-card color="accent" variant="tonal">
            <v-card-text class="text-center">
              <div class="text-h5">{{ formatPercent(store.kpis?.avgCtr) }}</div>
              <div class="text-caption">CTR</div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12" sm="6" md="3">
          <v-card color="warning" variant="tonal">
            <v-card-text class="text-center">
              <div class="text-h5">{{ formatNumber(store.kpis?.totalConversions) }}</div>
              <div class="text-caption">Conversions</div>
            </v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" sm="6" md="3">
          <v-card color="secondary" variant="tonal">
            <v-card-text class="text-center">
              <div class="text-h5">{{ formatNumber(store.kpis?.avgCpc) }}</div>
              <div class="text-caption">Avg CPC</div>
            </v-card-text>
          </v-card>
        </v-col>
        <v-col cols="12" sm="6" md="3">
          <v-card color="primary" variant="tonal">
            <v-card-text class="text-center">
              <div class="text-h5">{{ formatNumber(store.kpis?.avgRoas) }}</div>
              <div class="text-caption">ROAS</div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <!-- Campaigns Table -->
      <h2 class="mt-6 mb-3">Campaigns</h2>
      <v-card>
        <v-data-table
          :headers="campaignHeaders"
          :items="store.campaigns"
          item-value="id"
          hover
          no-data-text="No campaigns yet"
        >
          <template #item.status="{ item }">
            <v-chip
              :color="item.status === 'PUBLISHED' ? 'success' : item.status === 'DRAFT' ? 'grey' : 'warning'"
              size="small"
            >
              {{ item.status }}
            </v-chip>
          </template>
        </v-data-table>
      </v-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useDashboardStore } from '@/stores/dashboard'

const store = useDashboardStore()
const selectedClient = ref<string | null>(null)

const campaignHeaders = [
  { title: 'Name', key: 'name' },
  { title: 'Objective', key: 'objective' },
  { title: 'Status', key: 'status' },
  { title: 'Platform', key: 'platform' },
]

function formatNumber(val: number | null | undefined) {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(val)
}

function formatPercent(val: number | null | undefined) {
  if (val == null) return '—'
  return val.toFixed(2) + '%'
}

function onClientChange(clientId: string) {
  if (clientId) store.fetchDashboard(clientId)
}

onMounted(() => store.fetchClients())
</script>
