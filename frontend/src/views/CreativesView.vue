<template>
  <div>
    <h1 class="mb-4">Creatives</h1>

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
      Select a client to view creatives.
    </v-alert>

    <template v-if="selectedClient">
      <v-tabs v-model="tab" class="mb-4">
        <v-tab value="assets">Assets</v-tab>
        <v-tab value="packages">Packages</v-tab>
      </v-tabs>

      <v-window v-model="tab">
        <!-- Assets Tab -->
        <v-window-item value="assets">
          <v-card>
            <v-data-table
              :headers="assetHeaders"
              :items="store.assets"
              :loading="store.loading"
              item-value="id"
              hover
              no-data-text="No assets yet"
            >
              <template #item.sizeBytes="{ item }">
                {{ formatSize(item.sizeBytes) }}
              </template>
              <template #item.status="{ item }">
                <v-chip :color="assetStatusColor(item.status)" size="small">
                  {{ item.status }}
                </v-chip>
              </template>
              <template #item.createdAt="{ item }">
                {{ new Date(item.createdAt).toLocaleDateString() }}
              </template>
            </v-data-table>
          </v-card>
        </v-window-item>

        <!-- Packages Tab -->
        <v-window-item value="packages">
          <div class="d-flex justify-end mb-3">
            <v-btn color="primary" @click="showCreatePackage = true">
              <v-icon start>mdi-plus</v-icon> New Package
            </v-btn>
          </div>
          <v-card>
            <v-data-table
              :headers="packageHeaders"
              :items="store.packages"
              :loading="store.loading"
              item-value="id"
              hover
              no-data-text="No packages yet"
            >
              <template #item.status="{ item }">
                <v-chip :color="packageStatusColor(item.status)" size="small">
                  {{ item.status }}
                </v-chip>
              </template>
              <template #item.createdAt="{ item }">
                {{ new Date(item.createdAt).toLocaleDateString() }}
              </template>
              <template #item.actions="{ item }">
                <v-btn
                  v-if="item.status === 'DRAFT'"
                  size="small"
                  variant="text"
                  color="info"
                  title="Submit for review"
                  @click="store.submitPackage(item.id)"
                >
                  <v-icon>mdi-send</v-icon>
                </v-btn>
                <v-btn
                  v-if="item.status === 'IN_REVIEW'"
                  size="small"
                  variant="text"
                  color="success"
                  title="Approve"
                  @click="store.approvePackage(item.id)"
                >
                  <v-icon>mdi-check</v-icon>
                </v-btn>
              </template>
            </v-data-table>
          </v-card>
        </v-window-item>
      </v-window>
    </template>

    <!-- Create Package Dialog -->
    <v-dialog v-model="showCreatePackage" max-width="500">
      <v-card title="New Creative Package">
        <v-card-text>
          <v-text-field v-model="packageForm.name" label="Name" required />
          <v-select
            v-model="packageForm.objective"
            :items="['SALES', 'LEADS', 'TRAFFIC', 'AWARENESS']"
            label="Objective"
            clearable
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreatePackage = false">Cancel</v-btn>
          <v-btn color="primary" @click="onCreatePackage">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useCreativeStore } from '@/stores/creatives'
import { useClientStore } from '@/stores/clients'

const store = useCreativeStore()
const clientStore = useClientStore()
const selectedClient = ref<string | null>(null)
const tab = ref('assets')
const showCreatePackage = ref(false)
const packageForm = ref({ name: '', objective: '' })

const assetHeaders = [
  { title: 'Filename', key: 'originalFilename' },
  { title: 'Type', key: 'assetType' },
  { title: 'MIME', key: 'mimeType' },
  { title: 'Size', key: 'sizeBytes' },
  { title: 'Status', key: 'status' },
  { title: 'Created', key: 'createdAt' },
]

const packageHeaders = [
  { title: 'Name', key: 'name' },
  { title: 'Objective', key: 'objective' },
  { title: 'Status', key: 'status' },
  { title: 'Created By', key: 'createdBy' },
  { title: 'Approved By', key: 'approvedBy' },
  { title: 'Actions', key: 'actions', sortable: false },
]

function assetStatusColor(status: string) {
  const map: Record<string, string> = { UPLOADING: 'info', READY: 'success', ANALYZING: 'warning', FAILED: 'error' }
  return map[status] || 'grey'
}

function packageStatusColor(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'grey', IN_REVIEW: 'info', APPROVED: 'success',
    SCHEDULED: 'warning', USED: 'primary', ARCHIVED: 'error',
  }
  return map[status] || 'grey'
}

function formatSize(bytes: number) {
  if (bytes == null) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

async function onClientChange(clientId: string) {
  if (clientId) {
    await Promise.all([store.fetchAssets(clientId), store.fetchPackages(clientId)])
  }
}

async function onCreatePackage() {
  if (!selectedClient.value) return
  const payload: any = { name: packageForm.value.name }
  if (packageForm.value.objective) payload.objective = packageForm.value.objective
  await store.createPackage(selectedClient.value, payload)
  showCreatePackage.value = false
  packageForm.value = { name: '', objective: '' }
}

onMounted(() => clientStore.fetchClients())
</script>
