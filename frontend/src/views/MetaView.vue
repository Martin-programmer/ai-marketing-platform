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

    <v-alert v-if="store.error" type="error" class="mb-4" closable @click:close="store.error = null">
      {{ store.error }}
    </v-alert>

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
        <v-card-actions v-if="!store.connection || store.connection.status === 'DISCONNECTED' || store.connection.status === 'ERROR'">
          <v-btn color="primary" :loading="store.loading" @click="handleConnect" variant="flat">
            <v-icon start>mdi-facebook</v-icon>
            Connect with Facebook
          </v-btn>
          <v-btn color="secondary" @click="showManualDialog = true" variant="outlined">
            <v-icon start>mdi-key-variant</v-icon>
            Connect with Token
          </v-btn>
        </v-card-actions>
        <v-card-actions v-if="store.connection?.status === 'CONNECTED'">
          <v-btn color="error" variant="outlined" @click="handleDisconnect">
            <v-icon start>mdi-link-off</v-icon> Disconnect
          </v-btn>
        </v-card-actions>
      </v-card>

      <!-- Sync Controls -->
      <v-card class="mb-6" v-if="store.connection?.status === 'CONNECTED'">
        <v-card-title>
          <v-icon start>mdi-sync</v-icon>
          Data Sync
        </v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="auto">
              <v-btn color="primary" @click="handleSync('initial')" :loading="store.loading">
                <v-icon start>mdi-download</v-icon>
                Initial Sync (90 days)
              </v-btn>
            </v-col>
            <v-col cols="auto">
              <v-btn color="secondary" @click="handleSync('manual')" :loading="store.loading">
                <v-icon start>mdi-sync</v-icon>
                Manual Sync (30 days)
              </v-btn>
            </v-col>
          </v-row>
        </v-card-text>

        <!-- Recent Sync Jobs -->
        <v-card-text v-if="store.syncJobs.length > 0">
          <div class="text-subtitle-2 mb-2">Recent Sync Jobs</div>
          <v-table density="compact">
            <thead>
              <tr>
                <th>Type</th>
                <th>Status</th>
                <th>Started</th>
                <th>Finished</th>
                <th>Stats</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="job in store.syncJobs" :key="job.id">
                <td>{{ job.jobType }}</td>
                <td>
                  <v-chip :color="syncStatusColor(job.jobStatus)" size="small">
                    {{ job.jobStatus }}
                  </v-chip>
                </td>
                <td>{{ job.startedAt ? new Date(job.startedAt).toLocaleString() : '—' }}</td>
                <td>{{ job.finishedAt ? new Date(job.finishedAt).toLocaleString() : '—' }}</td>
                <td>{{ formatStats(job.statsJson) }}</td>
              </tr>
            </tbody>
          </v-table>
        </v-card-text>
      </v-card>
    </template>

    <!-- Manual Token Connect Dialog -->
    <v-dialog v-model="showManualDialog" max-width="700" persistent>
      <v-card>
        <v-card-title class="d-flex align-center">
          <v-icon start>mdi-key-variant</v-icon>
          Connect with Access Token
        </v-card-title>
        <v-card-text>
          <v-alert type="info" variant="tonal" class="mb-4" density="compact">
            <div class="font-weight-bold mb-1">How to get a token:</div>
            <ol class="pl-4">
              <li>Open <a href="https://developers.facebook.com/tools/explorer/" target="_blank">Graph API Explorer</a></li>
              <li>Select your App from the dropdown (top right)</li>
              <li>Click "Generate Access Token"</li>
              <li>Grant these permissions: <strong>ads_management, ads_read, pages_read_engagement, business_management</strong></li>
              <li>Copy the generated token and paste it below</li>
            </ol>
          </v-alert>

          <v-textarea
            v-model="manualToken"
            label="Access Token"
            placeholder="Paste your Meta access token here..."
            rows="3"
            variant="outlined"
            class="mb-2"
          />

          <v-btn
            color="primary"
            variant="outlined"
            @click="validateToken"
            :loading="validating"
            :disabled="!manualToken"
            class="mb-4"
          >
            <v-icon start>mdi-check-circle-outline</v-icon>
            Validate Token
          </v-btn>

          <v-alert v-if="tokenError" type="error" variant="tonal" class="mb-4" density="compact" closable @click:close="tokenError = null">
            {{ tokenError }}
          </v-alert>

          <template v-if="tokenData">
            <v-alert type="success" variant="tonal" class="mb-4" density="compact">
              Token is valid! Found {{ tokenData.adAccounts?.length || 0 }} ad account(s) and {{ tokenData.pages?.length || 0 }} page(s).
            </v-alert>

            <v-select
              v-model="selectedAdAccount"
              :items="tokenData.adAccounts"
              :item-title="(item: any) => `${item.name} (${item.id})`"
              item-value="id"
              label="Select Ad Account"
              variant="outlined"
              class="mb-2"
            />

            <v-select
              v-model="selectedPage"
              :items="tokenData.pages"
              :item-title="(item: any) => item.name"
              item-value="id"
              label="Facebook Page (optional)"
              variant="outlined"
              clearable
            />
          </template>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="closeManualDialog">Cancel</v-btn>
          <v-btn
            color="primary"
            variant="flat"
            @click="submitManualConnect"
            :loading="store.loading"
            :disabled="!manualToken || !selectedAdAccount"
          >
            <v-icon start>mdi-link</v-icon>
            Connect
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="3000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useMetaStore } from '@/stores/meta'
import { useClientStore } from '@/stores/clients'
import api from '@/api/client'

const store = useMetaStore()
const clientStore = useClientStore()
const selectedClient = ref<string | null>(null)
const snackbar = ref({ show: false, text: '', color: 'success' })

// Manual token connect state
const showManualDialog = ref(false)
const manualToken = ref('')
const validating = ref(false)
const tokenData = ref<any>(null)
const tokenError = ref<string | null>(null)
const selectedAdAccount = ref<string | null>(null)
const selectedPage = ref<string | null>(null)

function connectionStatusColor(status: string | undefined) {
  const map: Record<string, string> = { CONNECTED: 'success', DISCONNECTED: 'error', PENDING: 'warning', ERROR: 'error' }
  return map[status || ''] || 'grey'
}

function syncStatusColor(status: string) {
  const map: Record<string, string> = { PENDING: 'info', RUNNING: 'warning', SUCCESS: 'success', FAILED: 'error' }
  return map[status] || 'grey'
}

function formatStats(stats: string | null) {
  if (!stats) return '—'
  try {
    const parsed = typeof stats === 'string' ? JSON.parse(stats) : stats
    const parts: string[] = []
    if (parsed.campaigns) parts.push(`${parsed.campaigns} campaigns`)
    if (parsed.adsets) parts.push(`${parsed.adsets} adsets`)
    if (parsed.ads) parts.push(`${parsed.ads} ads`)
    if (parsed.insights_days) parts.push(`${parsed.insights_days} insight records`)
    return parts.join(', ') || '—'
  } catch {
    return '—'
  }
}

async function onClientChange(clientId: string) {
  if (!clientId) return
  await store.fetchConnection(clientId)
  if (store.connection?.status === 'CONNECTED') {
    await store.fetchSyncJobs(clientId)
  }
}

async function handleConnect() {
  if (!selectedClient.value) return
  const success = await store.startConnect(selectedClient.value)
  if (success) {
    snackbar.value = { show: true, text: 'Successfully connected to Meta!', color: 'success' }
    // Also load sync jobs now that we're connected
    await store.fetchSyncJobs(selectedClient.value)
  }
}

async function handleDisconnect() {
  if (!selectedClient.value) return
  await store.disconnect(selectedClient.value)
  snackbar.value = { show: true, text: 'Disconnected from Meta', color: 'info' }
}

async function handleSync(type: 'initial' | 'daily' | 'manual') {
  if (!selectedClient.value) return
  const result = await store.triggerSync(selectedClient.value, type)
  if (result) {
    snackbar.value = { show: true, text: `Sync completed: ${type}`, color: 'success' }
    // Refresh connection to get updated lastSyncAt
    await store.fetchConnection(selectedClient.value)
  } else {
    snackbar.value = { show: true, text: store.error || 'Sync failed', color: 'error' }
  }
}

async function validateToken() {
  validating.value = true
  tokenData.value = null
  tokenError.value = null
  try {
    const res = await api.post('/meta/validate-token', { accessToken: manualToken.value })
    tokenData.value = res.data
    if (res.data.adAccounts?.length > 0) {
      selectedAdAccount.value = res.data.adAccounts[0].id
    }
    if (res.data.pages?.length > 0) {
      selectedPage.value = res.data.pages[0].id
    }
  } catch (e: any) {
    tokenError.value = e.response?.data?.message || 'Token validation failed'
  } finally {
    validating.value = false
  }
}

async function submitManualConnect() {
  if (!selectedClient.value || !manualToken.value) return
  store.loading = true
  try {
    await api.post(`/clients/${selectedClient.value}/meta/connect/manual`, {
      accessToken: manualToken.value,
      adAccountId: selectedAdAccount.value,
      pageId: selectedPage.value,
      pixelId: null
    })
    closeManualDialog()
    snackbar.value = { show: true, text: 'Successfully connected to Meta!', color: 'success' }
    await store.fetchConnection(selectedClient.value)
    if (store.connection?.status === 'CONNECTED') {
      await store.fetchSyncJobs(selectedClient.value)
    }
  } catch (e: any) {
    tokenError.value = e.response?.data?.message || 'Connection failed'
  } finally {
    store.loading = false
  }
}

function closeManualDialog() {
  showManualDialog.value = false
  manualToken.value = ''
  tokenData.value = null
  tokenError.value = null
  selectedAdAccount.value = null
  selectedPage.value = null
}

onMounted(() => clientStore.fetchClients())
</script>
