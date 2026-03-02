<template>
  <div>
    <h1 class="mb-4">AI Suggestions</h1>

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
      Select a client to view AI suggestions.
    </v-alert>

    <template v-if="selectedClient">
      <!-- Filter chips -->
      <div class="d-flex ga-2 mb-4">
        <v-chip
          v-for="f in filters"
          :key="f.value"
          :color="statusFilter === f.value ? 'primary' : undefined"
          :variant="statusFilter === f.value ? 'flat' : 'outlined'"
          @click="onFilterChange(f.value)"
        >
          {{ f.label }}
        </v-chip>
      </div>

      <v-card>
        <v-data-table
          :headers="headers"
          :items="store.suggestions"
          :loading="store.loading"
          item-value="id"
          hover
          show-expand
          no-data-text="No suggestions"
        >
          <template #item.suggestionType="{ item }">
            <v-chip size="small" variant="tonal">
              <v-icon start size="small">{{ typeIcon(item.suggestionType) }}</v-icon>
              {{ item.suggestionType }}
            </v-chip>
          </template>
          <template #item.scopeType="{ item }">
            {{ item.scopeType }} / {{ item.scopeId }}
          </template>
          <template #item.riskLevel="{ item }">
            <v-chip :color="riskColor(item.riskLevel)" size="small">
              {{ item.riskLevel }}
            </v-chip>
          </template>
          <template #item.confidence="{ item }">
            {{ (item.confidence * 100).toFixed(0) }}%
          </template>
          <template #item.status="{ item }">
            <v-chip :color="statusColor(item.status)" size="small">
              {{ item.status }}
            </v-chip>
          </template>
          <template #item.createdAt="{ item }">
            {{ new Date(item.createdAt).toLocaleDateString() }}
          </template>
          <template #item.actions="{ item }">
            <v-btn
              v-if="item.status === 'PENDING'"
              size="small"
              variant="text"
              color="success"
              title="Approve"
              @click="store.approveSuggestion(item.id)"
            >
              <v-icon>mdi-check</v-icon>
            </v-btn>
            <v-btn
              v-if="item.status === 'PENDING'"
              size="small"
              variant="text"
              color="error"
              title="Reject"
              @click="store.rejectSuggestion(item.id)"
            >
              <v-icon>mdi-close</v-icon>
            </v-btn>
            <v-btn
              v-if="item.status === 'APPROVED'"
              size="small"
              variant="text"
              color="primary"
              title="Apply"
              @click="store.applySuggestion(item.id)"
            >
              <v-icon>mdi-play</v-icon>
            </v-btn>
          </template>

          <!-- Expanded row: rationale + payload -->
          <template #expanded-row="{ columns, item }">
            <tr>
              <td :colspan="columns.length" class="pa-4 bg-grey-lighten-4">
                <div v-if="item.rationale" class="mb-3">
                  <strong>Rationale:</strong>
                  <p class="mt-1">{{ item.rationale }}</p>
                </div>
                <div>
                  <strong>Payload:</strong>
                  <pre class="mt-1 pa-2 bg-grey-lighten-5 rounded" style="white-space: pre-wrap; font-size: 0.85em">{{ formatJson(item.payloadJson) }}</pre>
                </div>
              </td>
            </tr>
          </template>
        </v-data-table>
      </v-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useSuggestionStore } from '@/stores/suggestions'
import { useClientStore } from '@/stores/clients'

const store = useSuggestionStore()
const clientStore = useClientStore()
const selectedClient = ref<string | null>(null)
const statusFilter = ref<string | null>(null)

const filters = [
  { label: 'All', value: null as string | null },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Rejected', value: 'REJECTED' },
  { label: 'Applied', value: 'APPLIED' },
]

const headers = [
  { title: 'Type', key: 'suggestionType' },
  { title: 'Scope', key: 'scopeType' },
  { title: 'Risk Level', key: 'riskLevel' },
  { title: 'Confidence', key: 'confidence' },
  { title: 'Status', key: 'status' },
  { title: 'Created', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false },
]

function typeIcon(type: string) {
  const map: Record<string, string> = {
    BUDGET_ADJUST: 'mdi-currency-usd',
    PAUSE: 'mdi-pause',
    ENABLE: 'mdi-play',
    CREATIVE_TEST: 'mdi-image',
    DIAGNOSTIC: 'mdi-alert-circle',
  }
  return map[type] || 'mdi-lightbulb'
}

function riskColor(level: string) {
  const map: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'error' }
  return map[level] || 'grey'
}

function statusColor(status: string) {
  const map: Record<string, string> = { PENDING: 'info', APPROVED: 'success', REJECTED: 'error', APPLIED: 'primary', FAILED: 'error' }
  return map[status] || 'grey'
}

function formatJson(val: any) {
  if (!val) return '—'
  try {
    return typeof val === 'string' ? JSON.stringify(JSON.parse(val), null, 2) : JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}

async function onClientChange(clientId: string) {
  if (clientId) {
    statusFilter.value = null
    await store.fetchSuggestions(clientId)
  }
}

async function onFilterChange(status: string | null) {
  statusFilter.value = status
  if (selectedClient.value) {
    await store.fetchSuggestions(selectedClient.value, status ?? undefined)
  }
}

onMounted(() => clientStore.fetchClients())
</script>
