<template>
  <div>
    <h1 class="mb-4">Meta Integration</h1>

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
      Select a client to manage Meta integration.
    </v-alert>

    <template v-if="selectedClient">
      <!-- Connection Status Card -->
      <v-card class="mb-6">
        <v-card-title class="d-flex align-center">
          <v-icon start>mdi-facebook</v-icon>
          Connection Status
          <v-spacer />
          <v-chip
            :color="connectionStatusColor(store.connection?.status)"
            size="small"
          >
            {{ store.connection?.status || 'NOT CONNECTED' }}
          </v-chip>
        </v-card-title>
        <v-card-text v-if="store.connection">
          <v-row>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Ad Account ID</div>
              <div>{{ store.connection.adAccountId || '—' }}</div>
            </v-col>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Pixel ID</div>
              <div>{{ store.connection.pixelId || '—' }}</div>
            </v-col>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Page ID</div>
              <div>{{ store.connection.pageId || '—' }}</div>
            </v-col>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Connected At</div>
              <div>{{ store.connection.connectedAt ? new Date(store.connection.connectedAt).toLocaleString() : '—' }}</div>
            </v-col>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Last Sync At</div>
              <div>{{ store.connection.lastSyncAt ? new Date(store.connection.lastSyncAt).toLocaleString() : '—' }}</div>
            </v-col>
          </v-row>

          <!-- Error alert -->
          <v-alert
            v-if="store.connection.lastErrorCode || store.connection.lastErrorMessage"
            type="error"
            class="mt-4"
          >
            <strong>{{ store.connection.lastErrorCode }}:</strong> {{ store.connection.lastErrorMessage }}
          </v-alert>
        </v-card-text>
        <v-card-text v-else>
          <p class="text-grey">No Meta connection found for this client.</p>
        </v-card-text>
        <v-card-actions>
          <v-btn
            v-if="!store.connection || store.connection.status === 'DISCONNECTED'"
            color="primary"
            @click="onConnect"
          >
            <v-icon start>mdi-link</v-icon> Connect
          </v-btn>
          <v-btn
            v-if="store.connection?.status === 'CONNECTED'"
            color="error"
            variant="outlined"
            @click="onDisconnect"
          >
            <v-icon start>mdi-link-off</v-icon> Disconnect
          </v-btn>
          <v-btn
            v-if="store.connection?.status === 'ERROR'"
            color="warning"
            @click="onConnect"
          >
            <v-icon start>mdi-refresh</v-icon> Reconnect
          </v-btn>
        </v-card-actions>
      </v-card>

      <!-- Sync Section -->
      <h2 class="mb-3">Sync</h2>

      <v-card class="mb-4" v-if="store.syncStatus">
        <v-card-title>Last Sync Job</v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="12" sm="3">
              <div class="text-caption text-grey">Job Type</div>
              <div>{{ store.syncStatus.jobType }}</div>
            </v-col>
            <v-col cols="12" sm="3">
              <div class="text-caption text-grey">Status</div>
              <v-chip :color="syncStatusColor(store.syncStatus.status)" size="small">
                {{ store.syncStatus.status }}
              </v-chip>
            </v-col>
            <v-col cols="12" sm="3">
              <div class="text-caption text-grey">Started At</div>
              <div>{{ store.syncStatus.startedAt ? new Date(store.syncStatus.startedAt).toLocaleString() : '—' }}</div>
            </v-col>
            <v-col cols="12" sm="3">
              <div class="text-caption text-grey">Completed At</div>
              <div>{{ store.syncStatus.completedAt ? new Date(store.syncStatus.completedAt).toLocaleString() : '—' }}</div>
            </v-col>
          </v-row>
          <v-alert v-if="store.syncStatus.errorMessage" type="error" class="mt-3">
            {{ store.syncStatus.errorMessage }}
          </v-alert>
        </v-card-text>
      </v-card>

      <div class="d-flex ga-3">
        <v-btn color="primary" variant="outlined" @click="onSync('INITIAL')">
          <v-icon start>mdi-download</v-icon> Initial Sync
        </v-btn>
        <v-btn color="primary" variant="outlined" @click="onSync('DAILY')">
          <v-icon start>mdi-calendar-sync</v-icon> Daily Sync
        </v-btn>
        <v-btn color="primary" variant="outlined" @click="onSync('MANUAL')">
          <v-icon start>mdi-sync</v-icon> Manual Sync
        </v-btn>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMetaStore } from '@/stores/meta'
import { useClientStore } from '@/stores/clients'

const store = useMetaStore()
const clientStore = useClientStore()
const selectedClient = ref<string | null>(null)

function connectionStatusColor(status: string | undefined) {
  const map: Record<string, string> = { CONNECTED: 'success', DISCONNECTED: 'error', PENDING: 'warning', ERROR: 'error' }
  return map[status || ''] || 'grey'
}

function syncStatusColor(status: string) {
  const map: Record<string, string> = { PENDING: 'info', RUNNING: 'warning', SUCCESS: 'success', FAILED: 'error' }
  return map[status] || 'grey'
}

async function onClientChange(clientId: string) {
  if (clientId) {
    await Promise.all([store.fetchConnection(clientId), store.fetchSyncStatus(clientId)])
  }
}

async function onConnect() {
  if (!selectedClient.value) return
  await store.connectStart(selectedClient.value)
  await store.fetchConnection(selectedClient.value)
}

async function onDisconnect() {
  if (!selectedClient.value) return
  await store.disconnect(selectedClient.value)
}

async function onSync(jobType: string) {
  if (!selectedClient.value) return
  await store.triggerSync(selectedClient.value, jobType)
}

onMounted(() => clientStore.fetchClients())
</script>
