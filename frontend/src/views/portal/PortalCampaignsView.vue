<template>
  <div>
    <!-- Header -->
    <div class="d-flex align-center mb-6">
      <div>
        <h1 class="text-h4 font-weight-bold">Campaigns</h1>
        <p class="text-body-1 text-medium-emphasis mt-1">Your advertising campaigns</p>
      </div>
    </div>

    <!-- Loading -->
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- Empty state -->
    <v-alert v-if="!loading && campaigns.length === 0 && !error" type="info" variant="tonal">
      No campaigns to display yet. Your agency will set up campaigns for you.
    </v-alert>

    <!-- Campaigns Table -->
    <v-card v-if="campaigns.length > 0" variant="outlined">
      <v-data-table
        :headers="headers"
        :items="campaigns"
        item-value="id"
        hover
      >
        <template #item.name="{ item }">
          <div>
            <span class="font-weight-medium">{{ item.name }}</span>
            <div v-if="item.platform" class="text-caption text-medium-emphasis">{{ item.platform }}</div>
          </div>
        </template>

        <template #item.objective="{ item }">
          <v-chip size="small" variant="tonal" :color="objectiveColor(item.objective)">
            {{ item.objective }}
          </v-chip>
        </template>

        <template #item.status="{ item }">
          <v-chip :color="statusColor(item.status)" size="small">
            <v-icon start size="14">{{ statusIcon(item.status) }}</v-icon>
            {{ item.status }}
          </v-chip>
        </template>

        <template #item.createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>
      </v-data-table>
    </v-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import portalApi from '@/api/portal'

interface CampaignItem {
  id: string
  name: string
  platform: string
  objective: string
  status: string
  createdAt: string
}

const campaigns = ref<CampaignItem[]>([])
const loading = ref(false)
const error = ref<string | null>(null)

const headers = [
  { title: 'Campaign Name', key: 'name', sortable: true },
  { title: 'Objective', key: 'objective', sortable: true },
  { title: 'Status', key: 'status', sortable: true },
  { title: 'Created', key: 'createdAt', sortable: true },
]

function statusColor(status: string): string {
  const map: Record<string, string> = {
    PUBLISHED: 'success',
    ACTIVE: 'success',
    PAUSED: 'grey',
    ARCHIVED: 'grey',
  }
  return map[status] || 'grey'
}

function statusIcon(status: string): string {
  const map: Record<string, string> = {
    PUBLISHED: 'mdi-play-circle',
    PAUSED: 'mdi-pause-circle',
    ARCHIVED: 'mdi-archive',
  }
  return map[status] || 'mdi-help-circle'
}

function objectiveColor(objective: string): string {
  const map: Record<string, string> = {
    SALES: 'green',
    LEADS: 'blue',
    AWARENESS: 'purple',
    TRAFFIC: 'orange',
  }
  return map[objective] || 'grey'
}

function formatDate(dt: string): string {
  if (!dt) return '—'
  return new Date(dt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

async function loadCampaigns() {
  loading.value = true
  error.value = null
  try {
    const res = await portalApi.getCampaigns()
    campaigns.value = res.data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err.response?.data?.message || 'Failed to load campaigns'
  } finally {
    loading.value = false
  }
}

onMounted(loadCampaigns)
</script>
