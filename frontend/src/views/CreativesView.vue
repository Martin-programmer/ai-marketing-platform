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
      <!-- Upload Zone -->
      <v-card class="mb-6">
        <v-card-title>
          <v-icon start>mdi-cloud-upload</v-icon>
          Upload Creatives
        </v-card-title>
        <v-card-text>
          <div
            class="upload-zone pa-8 text-center rounded-lg"
            :class="{ 'upload-zone-active': isDragging }"
            @dragover.prevent="isDragging = true"
            @dragleave.prevent="isDragging = false"
            @drop.prevent="handleDrop"
          >
            <v-icon size="48" color="grey-lighten-1" class="mb-2">mdi-cloud-upload-outline</v-icon>
            <div class="text-body-1 text-grey mb-2">Drag & drop files here or</div>
            <v-btn color="primary" variant="outlined" @click="($refs.fileInput as HTMLInputElement)?.click()">
              Browse Files
            </v-btn>
            <input
              ref="fileInput"
              type="file"
              multiple
              accept="image/*,video/*,.pdf"
              style="display: none"
              @change="handleFileSelect"
            />
            <div class="text-caption text-grey mt-2">
              Supports: JPG, PNG, GIF, MP4, MOV, PDF — Max 100MB per file
            </div>
          </div>

          <v-list v-if="uploadQueue.length > 0" class="mt-4">
            <v-list-item v-for="upload in uploadQueue" :key="upload.id">
              <template #prepend>
                <v-icon :color="uploadStatusColor(upload.status)">
                  {{ uploadStatusIcon(upload.status) }}
                </v-icon>
              </template>
              <v-list-item-title>{{ upload.fileName }}</v-list-item-title>
              <v-list-item-subtitle>
                {{ formatSize(upload.size) }} — {{ upload.status }}
              </v-list-item-subtitle>
              <template #append>
                <v-progress-circular
                  v-if="upload.status === 'uploading'"
                  :model-value="upload.progress"
                  size="24" width="3" color="primary"
                />
                <v-icon v-if="upload.status === 'done'" color="success">mdi-check-circle</v-icon>
                <v-icon v-if="upload.status === 'error'" color="error">mdi-alert-circle</v-icon>
              </template>
            </v-list-item>
          </v-list>
        </v-card-text>
      </v-card>

      <v-tabs v-model="tab" class="mb-4">
        <v-tab value="assets">Assets</v-tab>
        <v-tab value="packages">Packages</v-tab>
      </v-tabs>

      <v-window v-model="tab">
        <!-- Assets Tab -->
        <v-window-item value="assets">
          <v-row v-if="store.assets.length > 0">
            <v-col v-for="asset in store.assets" :key="asset.id" cols="12" sm="6" md="4" lg="3">
              <v-card variant="outlined" class="creative-card">
                <div class="creative-thumbnail d-flex align-center justify-center"
                     style="height: 180px; background: #f5f5f5; overflow: hidden;">
                  <img v-if="asset.assetType === 'IMAGE' && asset.thumbnailUrl"
                       :src="asset.thumbnailUrl"
                       style="max-width: 100%; max-height: 100%; object-fit: cover;" />
                  <v-icon v-else-if="asset.assetType === 'VIDEO'" size="48" color="grey">mdi-video</v-icon>
                  <v-icon v-else size="48" color="grey">mdi-image</v-icon>
                </div>
                <v-card-text class="pa-3">
                  <div class="text-body-2 text-truncate">{{ asset.originalFilename }}</div>
                  <div class="text-caption text-grey">
                    {{ formatSize(asset.sizeBytes) }}
                    <span v-if="asset.widthPx"> · {{ asset.widthPx }}×{{ asset.heightPx }}</span>
                  </div>
                  <v-chip :color="assetStatusColor(asset.status)" size="x-small" class="mt-1">
                    {{ asset.status }}
                  </v-chip>
                </v-card-text>
                <v-card-actions class="pa-2 pt-0">
                  <v-btn icon size="small" @click="viewAsset(asset)" title="View/Download">
                    <v-icon size="small">mdi-eye</v-icon>
                  </v-btn>
                  <v-spacer />
                  <v-btn icon size="small" color="error" @click="deleteAsset(asset)" title="Delete">
                    <v-icon size="small">mdi-delete</v-icon>
                  </v-btn>
                </v-card-actions>
              </v-card>
            </v-col>
          </v-row>
          <v-alert v-else type="info" variant="tonal">
            No creatives uploaded yet. Drag & drop files above to get started.
          </v-alert>
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

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="3000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useCreativeStore } from '@/stores/creatives'
import { useClientStore } from '@/stores/clients'
import api from '@/api/client'

const store = useCreativeStore()
const clientStore = useClientStore()
const selectedClient = ref<string | null>(null)
const tab = ref('assets')
const showCreatePackage = ref(false)
const packageForm = ref({ name: '', objective: '' })
const isDragging = ref(false)
const snackbar = ref({ show: false, text: '', color: 'success' })

interface UploadItem {
  id: string
  fileName: string
  size: number
  status: 'pending' | 'initiating' | 'uploading' | 'completing' | 'done' | 'error'
  progress: number
}
const uploadQueue = ref<UploadItem[]>([])

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
  if (!clientId) return
  await Promise.all([store.fetchAssets(clientId), store.fetchPackages(clientId)])
  // Load thumbnail URLs for images
  for (const asset of store.assets) {
    if (asset.assetType === 'IMAGE' && asset.status === 'READY') {
      try {
        const res = await api.get(`/creatives/${asset.id}/url`)
        asset.thumbnailUrl = res.data.url
      } catch { /* ignore */ }
    }
  }
}

function handleDrop(event: DragEvent) {
  isDragging.value = false
  const files = event.dataTransfer?.files
  if (files) uploadFiles(Array.from(files))
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files) {
    uploadFiles(Array.from(input.files))
    input.value = ''
  }
}

async function uploadFiles(files: File[]) {
  if (!selectedClient.value) return
  for (const file of files) {
    if (file.size > 100 * 1024 * 1024) {
      snackbar.value = { show: true, text: `${file.name} is too large (max 100MB)`, color: 'error' }
      continue
    }
    const item: UploadItem = {
      id: Math.random().toString(36).substring(7),
      fileName: file.name, size: file.size, status: 'pending', progress: 0
    }
    uploadQueue.value.push(item)
    try {
      item.status = 'initiating'
      const initRes = await api.post(`/clients/${selectedClient.value}/creatives/uploads`, {
        fileName: file.name,
        mimeType: file.type || 'application/octet-stream',
        sizeBytes: file.size,
      })
      const { assetId, presignedPutUrl } = initRes.data

      item.status = 'uploading'
      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('PUT', presignedPutUrl)
        xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
        xhr.upload.addEventListener('progress', (e) => {
          if (e.lengthComputable) item.progress = Math.round((e.loaded / e.total) * 100)
        })
        xhr.addEventListener('load', () => xhr.status >= 200 && xhr.status < 300 ? resolve() : reject(new Error(`S3 upload failed: ${xhr.status}`)))
        xhr.addEventListener('error', () => reject(new Error('Network error')))
        xhr.send(file)
      })

      item.status = 'completing'
      await api.post(`/creatives/${assetId}/uploads/complete`, {})
      item.status = 'done'
      item.progress = 100

      await onClientChange(selectedClient.value)
    } catch (e: any) {
      item.status = 'error'
      console.error('Upload failed:', e)
    }
  }
  setTimeout(() => {
    uploadQueue.value = uploadQueue.value.filter(u => u.status !== 'done')
  }, 3000)
}

function uploadStatusColor(status: string) {
  return status === 'done' ? 'success' : status === 'error' ? 'error' : 'primary'
}

function uploadStatusIcon(status: string) {
  return status === 'done' ? 'mdi-check-circle' : status === 'error' ? 'mdi-alert-circle' : 'mdi-cloud-upload'
}

async function viewAsset(asset: any) {
  try {
    const res = await api.get(`/creatives/${asset.id}/url`)
    window.open(res.data.url, '_blank')
  } catch { snackbar.value = { show: true, text: 'Failed to get URL', color: 'error' } }
}

async function deleteAsset(asset: any) {
  if (!confirm(`Delete ${asset.originalFilename}?`)) return
  try {
    await api.delete(`/creatives/${asset.id}`)
    snackbar.value = { show: true, text: 'Deleted', color: 'success' }
    if (selectedClient.value) await onClientChange(selectedClient.value)
  } catch { snackbar.value = { show: true, text: 'Delete failed', color: 'error' } }
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

<style scoped>
.upload-zone {
  border: 2px dashed #ccc;
  transition: all 0.2s;
  cursor: pointer;
}
.upload-zone:hover,
.upload-zone-active {
  border-color: #1976D2;
  background-color: rgba(25, 118, 210, 0.04);
}
.creative-card {
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.creative-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}
</style>
