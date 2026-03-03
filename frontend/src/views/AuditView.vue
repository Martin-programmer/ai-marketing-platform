<template>
  <div>
    <!-- Header -->
    <div class="d-flex align-center mb-6">
      <div>
        <h1 class="text-h4 font-weight-bold">Audit Log</h1>
        <p class="text-body-1 text-medium-emphasis mt-1">Activity trail for your agency</p>
      </div>
      <v-spacer />
      <v-btn variant="tonal" prepend-icon="mdi-refresh" @click="loadLogs" :loading="store.loading">
        Refresh
      </v-btn>
    </div>

    <!-- Filters -->
    <v-card variant="outlined" class="mb-4 pa-4">
      <v-row dense>
        <v-col cols="12" md="3">
          <v-select
            v-model="filterEntityType"
            :items="entityTypes"
            label="Entity Type"
            clearable
            density="compact"
            variant="outlined"
            hide-details
          />
        </v-col>
        <v-col cols="12" md="3">
          <v-select
            v-model="filterAction"
            :items="actionTypes"
            label="Action"
            clearable
            density="compact"
            variant="outlined"
            hide-details
          />
        </v-col>
        <v-col cols="12" md="3">
          <v-text-field
            v-model="filterEntityId"
            label="Entity ID"
            clearable
            density="compact"
            variant="outlined"
            hide-details
            placeholder="UUID"
          />
        </v-col>
        <v-col cols="12" md="2">
          <v-text-field
            v-model.number="filterLimit"
            label="Limit"
            type="number"
            density="compact"
            variant="outlined"
            hide-details
            min="1"
            max="500"
          />
        </v-col>
        <v-col cols="12" md="1" class="d-flex align-center">
          <v-btn icon variant="tonal" color="primary" @click="loadLogs">
            <v-icon>mdi-magnify</v-icon>
          </v-btn>
        </v-col>
      </v-row>
    </v-card>

    <!-- Loading -->
    <v-progress-linear v-if="store.loading" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="store.error" type="error" variant="tonal" class="mb-4" closable>
      {{ store.error }}
    </v-alert>

    <!-- Empty state -->
    <v-alert v-if="!store.loading && store.logs.length === 0 && !store.error" type="info" variant="tonal">
      No audit log entries found for the current filters.
    </v-alert>

    <!-- Timeline -->
    <v-card v-if="store.logs.length > 0" variant="outlined">
      <v-data-table
        :headers="headers"
        :items="store.logs"
        item-value="id"
        hover
        density="comfortable"
        items-per-page="25"
      >
        <template #item.action="{ item }">
          <v-chip :color="actionColor(item.action)" size="small" variant="tonal">
            <v-icon start size="14">{{ actionIcon(item.action) }}</v-icon>
            {{ item.action }}
          </v-chip>
        </template>

        <template #item.entityType="{ item }">
          <span class="font-weight-medium text-body-2">{{ item.entityType }}</span>
        </template>

        <template #item.entityId="{ item }">
          <span class="text-body-2 text-medium-emphasis">{{ shortId(item.entityId) }}</span>
        </template>

        <template #item.actorRole="{ item }">
          <v-chip size="x-small" variant="flat" :color="roleColor(item.actorRole)">
            {{ item.actorRole }}
          </v-chip>
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
    <v-dialog v-model="detailDialog" max-width="700">
      <v-card v-if="selectedLog">
        <v-card-title class="d-flex align-center">
          <v-icon start color="primary">mdi-history</v-icon>
          Audit Entry Detail
          <v-spacer />
          <v-chip :color="actionColor(selectedLog.action)" size="small">
            {{ selectedLog.action }}
          </v-chip>
        </v-card-title>

        <v-divider />

        <v-card-text class="pa-4">
          <v-row dense>
            <v-col cols="6">
              <div class="text-overline text-medium-emphasis">Entity Type</div>
              <div class="text-body-1">{{ selectedLog.entityType }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-overline text-medium-emphasis">Entity ID</div>
              <div class="text-body-2">{{ selectedLog.entityId || '—' }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-overline text-medium-emphasis">Actor Role</div>
              <div class="text-body-1">{{ selectedLog.actorRole }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-overline text-medium-emphasis">Actor User ID</div>
              <div class="text-body-2">{{ selectedLog.actorUserId || '—' }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-overline text-medium-emphasis">Timestamp</div>
              <div class="text-body-1">{{ formatDateFull(selectedLog.createdAt) }}</div>
            </v-col>
            <v-col cols="6">
              <div class="text-overline text-medium-emphasis">Correlation ID</div>
              <div class="text-body-2 text-medium-emphasis">{{ selectedLog.correlationId }}</div>
            </v-col>
          </v-row>

          <!-- Before / After -->
          <template v-if="selectedLog.beforeJson || selectedLog.afterJson">
            <v-divider class="my-4" />
            <v-row dense>
              <v-col v-if="selectedLog.beforeJson" cols="12" md="6">
                <div class="text-overline text-medium-emphasis mb-1">Before</div>
                <v-sheet color="red-lighten-5" rounded class="pa-3">
                  <pre class="text-body-2" style="white-space: pre-wrap; max-height: 250px; overflow-y: auto;">{{ formatJson(selectedLog.beforeJson) }}</pre>
                </v-sheet>
              </v-col>
              <v-col v-if="selectedLog.afterJson" cols="12" md="6">
                <div class="text-overline text-medium-emphasis mb-1">After</div>
                <v-sheet color="green-lighten-5" rounded class="pa-3">
                  <pre class="text-body-2" style="white-space: pre-wrap; max-height: 250px; overflow-y: auto;">{{ formatJson(selectedLog.afterJson) }}</pre>
                </v-sheet>
              </v-col>
            </v-row>
          </template>
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
import { useAuditStore, type AuditLogEntry } from '@/stores/audit'

const store = useAuditStore()

const filterEntityType = ref<string | null>(null)
const filterAction = ref<string | null>(null)
const filterEntityId = ref<string | null>(null)
const filterLimit = ref(100)
const detailDialog = ref(false)
const selectedLog = ref<AuditLogEntry | null>(null)

const entityTypes = [
  'CLIENT', 'Campaign', 'Adset', 'Ad',
  'CREATIVE_ASSET', 'CREATIVE_PACKAGE', 'COPY_VARIANT',
  'AiSuggestion', 'Report',
]

const actionTypes = [
  'CLIENT_CREATE', 'CLIENT_UPDATE', 'CLIENT_PAUSE', 'CLIENT_ACTIVATE',
  'CAMPAIGN_CREATE', 'CAMPAIGN_PAUSE', 'CAMPAIGN_RESUME', 'CAMPAIGN_PUBLISH',
  'ADSET_CREATE', 'AD_CREATE',
  'CREATIVE_UPLOAD', 'CREATIVE_APPROVE',
  'SUGGESTION_APPROVE', 'SUGGESTION_REJECT', 'SUGGESTION_APPLY',
  'REPORT_GENERATE', 'REPORT_SEND',
  'META_CONNECT', 'META_DISCONNECT',
  'USER_INVITE', 'ROLE_CHANGE', 'PUBLISH',
]

const headers = [
  { title: 'Action', key: 'action', sortable: true },
  { title: 'Entity', key: 'entityType', sortable: true },
  { title: 'Entity ID', key: 'entityId', sortable: false },
  { title: 'Actor', key: 'actorRole', sortable: true },
  { title: 'Timestamp', key: 'createdAt', sortable: true },
  { title: '', key: 'actions', sortable: false, width: '60px' },
]

function actionColor(action: string): string {
  if (action.includes('CREATE') || action.includes('UPLOAD')) return 'success'
  if (action.includes('APPROVE') || action.includes('APPLY') || action.includes('ACTIVATE')) return 'info'
  if (action.includes('PAUSE') || action.includes('REJECT')) return 'warning'
  if (action.includes('DISCONNECT')) return 'error'
  if (action.includes('PUBLISH') || action.includes('SEND')) return 'primary'
  return 'grey'
}

function actionIcon(action: string): string {
  if (action.includes('CREATE') || action.includes('UPLOAD')) return 'mdi-plus-circle'
  if (action.includes('APPROVE')) return 'mdi-check-circle'
  if (action.includes('APPLY')) return 'mdi-play-circle'
  if (action.includes('REJECT')) return 'mdi-close-circle'
  if (action.includes('PAUSE')) return 'mdi-pause-circle'
  if (action.includes('ACTIVATE') || action.includes('RESUME')) return 'mdi-play'
  if (action.includes('PUBLISH') || action.includes('SEND')) return 'mdi-send'
  if (action.includes('UPDATE')) return 'mdi-pencil'
  if (action.includes('CONNECT')) return 'mdi-link'
  if (action.includes('DISCONNECT')) return 'mdi-link-off'
  return 'mdi-history'
}

function roleColor(role: string): string {
  const map: Record<string, string> = {
    OWNER_ADMIN: 'deep-purple',
    AGENCY_ADMIN: 'blue',
    AGENCY_USER: 'teal',
    CLIENT_USER: 'green',
    SYSTEM: 'grey',
  }
  return map[role] || 'grey'
}

function shortId(id: string | null): string {
  if (!id) return '—'
  return id.substring(0, 8) + '…'
}

function formatDate(dt: string): string {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('en-US', {
    month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
  })
}

function formatDateFull(dt: string): string {
  if (!dt) return '—'
  return new Date(dt).toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

function formatJson(raw: string | null): string {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function openDetail(entry: AuditLogEntry) {
  selectedLog.value = entry
  detailDialog.value = true
}

function loadLogs() {
  store.fetchLogs({
    entityType: filterEntityType.value || undefined,
    action: filterAction.value || undefined,
    entityId: filterEntityId.value || undefined,
    limit: filterLimit.value,
  })
}

onMounted(loadLogs)
</script>
