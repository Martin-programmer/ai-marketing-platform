<template>
  <div>
    <!-- Header -->
    <div class="d-flex align-center mb-6">
      <div>
        <h1 class="text-h4 font-weight-bold">AI Suggestions</h1>
        <p class="text-body-1 text-medium-emphasis mt-1">Optimisation suggestions generated for your campaigns</p>
      </div>
    </div>

    <!-- Loading -->
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- Empty state -->
    <v-alert v-if="!loading && suggestions.length === 0 && !error" type="info" variant="tonal">
      No suggestions available yet. AI-powered recommendations will appear here as they are generated.
    </v-alert>

    <!-- Suggestions Table -->
    <v-card v-if="suggestions.length > 0" variant="outlined">
      <v-data-table
        :headers="headers"
        :items="suggestions"
        item-value="id"
        hover
      >
        <template #item.type="{ item }">
          <v-chip size="small" variant="tonal" :color="typeColor(item.type)">
            <v-icon start size="14">{{ typeIcon(item.type) }}</v-icon>
            {{ item.type }}
          </v-chip>
        </template>

        <template #item.status="{ item }">
          <v-chip :color="statusColor(item.status)" size="small">
            {{ item.status }}
          </v-chip>
        </template>

        <template #item.rationale="{ item }">
          <span class="text-body-2">{{ truncate(item.rationale, 120) }}</span>
        </template>

        <template #item.createdAt="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>

        <template #item.actions="{ item }">
          <v-btn icon variant="text" size="small" @click="openDetail(item)">
            <v-icon>mdi-eye</v-icon>
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <!-- Detail Dialog -->
    <v-dialog v-model="detailDialog" max-width="650">
      <v-card v-if="selectedSuggestion">
        <v-card-title class="d-flex align-center">
          <v-chip :color="typeColor(selectedSuggestion.type)" size="small" class="mr-3">
            <v-icon start size="14">{{ typeIcon(selectedSuggestion.type) }}</v-icon>
            {{ selectedSuggestion.type }}
          </v-chip>
          Suggestion Detail
          <v-spacer />
          <v-chip :color="statusColor(selectedSuggestion.status)" size="small">
            {{ selectedSuggestion.status }}
          </v-chip>
        </v-card-title>

        <v-divider />

        <v-card-text class="pa-4">
          <div class="mb-4">
            <div class="text-overline text-medium-emphasis mb-1">Rationale</div>
            <p class="text-body-1">{{ selectedSuggestion.rationale || '—' }}</p>
          </div>

          <div v-if="selectedSuggestion.payload" class="mb-4">
            <div class="text-overline text-medium-emphasis mb-1">Payload</div>
            <v-sheet color="grey-lighten-4" rounded class="pa-3">
              <pre class="text-body-2" style="white-space: pre-wrap;">{{ formatPayload(selectedSuggestion.payload) }}</pre>
            </v-sheet>
          </div>

          <div class="text-caption text-medium-emphasis">
            Created: {{ formatDate(selectedSuggestion.createdAt) }}
          </div>
        </v-card-text>

        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="detailDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import portalApi from '@/api/portal'

interface SuggestionItem {
  id: string
  type: string
  status: string
  rationale: string
  payload: unknown
  createdAt: string
}

const suggestions = ref<SuggestionItem[]>([])
const loading = ref(false)
const error = ref<string | null>(null)
const detailDialog = ref(false)
const selectedSuggestion = ref<SuggestionItem | null>(null)

const headers = [
  { title: 'Type', key: 'type', sortable: true },
  { title: 'Status', key: 'status', sortable: true },
  { title: 'Rationale', key: 'rationale', sortable: false },
  { title: 'Created', key: 'createdAt', sortable: true },
  { title: '', key: 'actions', sortable: false, width: '60px' },
]

function statusColor(status: string): string {
  const map: Record<string, string> = {
    APPLIED: 'success',
    APPROVED: 'info',
    PENDING: 'warning',
    REJECTED: 'error',
  }
  return map[status] || 'grey'
}

function typeColor(type: string): string {
  const map: Record<string, string> = {
    BUDGET: 'green',
    BID: 'blue',
    TARGETING: 'purple',
    CREATIVE: 'orange',
    KEYWORD: 'teal',
    SCHEDULE: 'indigo',
  }
  return map[type] || 'grey'
}

function typeIcon(type: string): string {
  const map: Record<string, string> = {
    BUDGET: 'mdi-cash',
    BID: 'mdi-gavel',
    TARGETING: 'mdi-target',
    CREATIVE: 'mdi-brush',
    KEYWORD: 'mdi-key',
    SCHEDULE: 'mdi-calendar-clock',
  }
  return map[type] || 'mdi-lightbulb'
}

function truncate(text: string, max: number): string {
  if (!text) return '—'
  return text.length > max ? text.substring(0, max) + '…' : text
}

function formatDate(dt: string): string {
  if (!dt) return '—'
  return new Date(dt).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' })
}

function formatPayload(payload: unknown): string {
  if (!payload) return '—'
  try {
    if (typeof payload === 'string') return payload
    return JSON.stringify(payload, null, 2)
  } catch {
    return String(payload)
  }
}

function openDetail(item: SuggestionItem) {
  selectedSuggestion.value = item
  detailDialog.value = true
}

async function loadSuggestions() {
  loading.value = true
  error.value = null
  try {
    const res = await portalApi.getSuggestions()
    suggestions.value = res.data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err.response?.data?.message || 'Failed to load suggestions'
  } finally {
    loading.value = false
  }
}

onMounted(loadSuggestions)
</script>
