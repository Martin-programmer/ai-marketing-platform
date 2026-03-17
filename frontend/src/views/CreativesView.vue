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
              <v-card :ref="setAssetCardRef(asset.id)" variant="outlined" class="creative-card" @click="openAssetDetail(asset)">
                <div class="creative-thumbnail d-flex align-center justify-center"
                     style="height: 180px; background: #f5f5f5; overflow: hidden;">
                  <img v-if="asset.assetType === 'IMAGE' && asset.thumbnailUrl"
                       :src="asset.thumbnailUrl"
                       style="max-width: 100%; max-height: 100%; object-fit: cover;" />
                  <v-icon v-else-if="asset.assetType === 'VIDEO'" size="48" color="grey">mdi-video</v-icon>
                  <v-icon v-else size="48" color="grey">mdi-image</v-icon>
                  <v-chip
                    v-if="analysisForAsset(asset.id)?.qualityScore != null"
                    class="analysis-score-chip"
                    :color="qualityScoreColor(Number(analysisForAsset(asset.id)?.qualityScore))"
                    size="small"
                  >
                    AI {{ Math.round(Number(analysisForAsset(asset.id)?.qualityScore)) }}
                  </v-chip>
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
                  <div class="d-flex flex-wrap ga-1 mt-2 min-summary-row">
                    <template v-if="parsedAnalysisForAsset(asset.id)">
                      <v-chip
                        v-if="parsedAnalysisForAsset(asset.id)?.colorPalette?.length"
                        size="x-small"
                        variant="tonal"
                        color="secondary"
                      >
                        {{ parsedAnalysisForAsset(asset.id)?.colorPalette?.length }} colors
                      </v-chip>
                      <v-chip
                        v-if="parsedAnalysisForAsset(asset.id)?.strengths?.length"
                        size="x-small"
                        variant="tonal"
                        color="success"
                      >
                        {{ parsedAnalysisForAsset(asset.id)?.strengths?.length }} strengths
                      </v-chip>
                      <v-chip
                        v-if="(store.copyVariantsByAsset[asset.id] ?? []).length"
                        size="x-small"
                        variant="tonal"
                        color="primary"
                      >
                        {{ store.copyVariantsByAsset[asset.id]?.length ?? 0 }} copy variants
                      </v-chip>
                    </template>
                    <v-progress-circular
                      v-else-if="store.analysisLoadingByAsset[asset.id] || store.copyVariantsLoadingByAsset[asset.id]"
                      indeterminate
                      size="16"
                      width="2"
                      color="primary"
                    />
                    <span v-else class="text-caption text-medium-emphasis">AI insights load on demand</span>
                  </div>
                  <div v-if="platformFitEntries(asset.id).length" class="d-flex flex-wrap ga-2 mt-2">
                    <div
                      v-for="platform in platformFitEntries(asset.id).slice(0, 3)"
                      :key="platform.key"
                      class="platform-pill"
                    >
                      <span class="text-caption text-medium-emphasis">{{ platform.label }}</span>
                      <div class="d-flex ga-1">
                        <v-icon
                          v-for="index in 5"
                          :key="`${platform.key}-${index}`"
                          size="10"
                          :color="index <= platform.rating ? 'primary' : 'grey-lighten-1'"
                        >
                          mdi-circle
                        </v-icon>
                      </div>
                    </div>
                  </div>
                </v-card-text>
                <v-card-actions class="pa-2 pt-0">
                  <v-btn icon size="small" @click.stop="viewAsset(asset)" title="View/Download">
                    <v-icon size="small">mdi-eye</v-icon>
                  </v-btn>
                  <v-btn
                    icon
                    size="small"
                    :loading="store.analyzeRunningByAsset[asset.id]"
                    title="Analyze creative"
                    @click.stop="analyzeAsset(asset)"
                  >
                    <v-icon size="small">mdi-brain</v-icon>
                  </v-btn>
                  <v-btn
                    icon
                    size="small"
                    :loading="store.generateCopyRunningByAsset[asset.id]"
                    title="Generate copy"
                    @click.stop="generateCopyForAsset(asset)"
                  >
                    <v-icon size="small">mdi-message-text-outline</v-icon>
                  </v-btn>
                  <v-spacer />
                  <v-btn icon size="small" color="error" @click.stop="deleteAsset(asset)" title="Delete">
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
          <PackageBuilder :client-id="selectedClient" />
        </v-window-item>
      </v-window>
    </template>

    <v-dialog v-model="showCreativeDetail" max-width="1100">
      <v-card v-if="activeAsset">
        <v-card-title class="d-flex align-center ga-3 flex-wrap">
          <div>
            <div class="text-h6">{{ activeAsset.originalFilename }}</div>
            <div class="text-caption text-medium-emphasis">
              {{ formatSize(activeAsset.sizeBytes) }}
              <span v-if="activeAsset.widthPx"> · {{ activeAsset.widthPx }}×{{ activeAsset.heightPx }}</span>
            </div>
          </div>
          <v-spacer />
          <v-chip :color="assetStatusColor(activeAsset.status)" size="small">{{ activeAsset.status }}</v-chip>
          <v-chip
            v-if="analysisForAsset(activeAsset.id)?.qualityScore != null"
            :color="qualityScoreColor(Number(analysisForAsset(activeAsset.id)?.qualityScore))"
            size="small"
          >
            Quality {{ Math.round(Number(analysisForAsset(activeAsset.id)?.qualityScore)) }}/100
          </v-chip>
        </v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="12" md="5">
              <div class="detail-preview rounded-lg">
                <img
                  v-if="activeAsset.assetType === 'IMAGE' && previewUrlForAsset(activeAsset.id)"
                  :src="previewUrlForAsset(activeAsset.id)"
                  class="detail-preview-media"
                />
                <v-icon v-else-if="activeAsset.assetType === 'VIDEO'" size="72" color="grey">mdi-video</v-icon>
                <v-icon v-else size="72" color="grey">mdi-file-document-outline</v-icon>
              </div>

              <div class="d-flex flex-wrap ga-2 mt-4">
                <v-btn variant="outlined" prepend-icon="mdi-eye" @click="viewAsset(activeAsset)">Open asset</v-btn>
                <v-btn
                  color="primary"
                  variant="tonal"
                  prepend-icon="mdi-brain"
                  :loading="store.analyzeRunningByAsset[activeAsset.id]"
                  @click="analyzeAsset(activeAsset)"
                >
                  {{ analysisForAsset(activeAsset.id) ? 'Re-analyze' : 'Analyze' }}
                </v-btn>
                <v-btn
                  color="primary"
                  prepend-icon="mdi-message-text-outline"
                  :loading="store.generateCopyRunningByAsset[activeAsset.id]"
                  @click="generateCopyForAsset(activeAsset)"
                >
                  {{ (store.copyVariantsByAsset[activeAsset.id] ?? []).length ? 'Regenerate copy' : 'Generate copy' }}
                </v-btn>
              </div>

              <v-list density="compact" class="mt-4" lines="two">
                <v-list-item title="Created" :subtitle="formatDate(activeAsset.createdAt)" />
                <v-list-item title="MIME type" :subtitle="activeAsset.mimeType || '—'" />
                <v-list-item title="Checksum" :subtitle="activeAsset.checksumSha256 || '—'" />
              </v-list>
            </v-col>

            <v-col cols="12" md="7">
              <div class="d-flex align-center mb-3">
                <div class="text-subtitle-1 font-weight-medium">AI creative analysis</div>
                <v-spacer />
                <v-progress-circular
                  v-if="store.analysisLoadingByAsset[activeAsset.id]"
                  indeterminate
                  size="18"
                  width="2"
                  color="primary"
                />
              </div>

              <template v-if="parsedAnalysisForAsset(activeAsset.id)">
                <v-alert type="info" variant="tonal" class="mb-4">
                  {{ parsedAnalysisForAsset(activeAsset.id)?.overallSummary || 'Analysis loaded.' }}
                </v-alert>

                <div class="d-flex flex-wrap ga-2 mb-4">
                  <v-chip
                    v-for="platform in platformFitEntries(activeAsset.id)"
                    :key="platform.key"
                    size="small"
                    variant="tonal"
                    color="primary"
                  >
                    {{ platform.label }} {{ platform.rating }}/5
                  </v-chip>
                </div>

                <div v-if="parsedAnalysisForAsset(activeAsset.id)?.colorPalette?.length" class="mb-4">
                  <div class="text-subtitle-2 mb-2">Palette</div>
                  <div class="d-flex ga-2 flex-wrap">
                    <div
                      v-for="color in parsedAnalysisForAsset(activeAsset.id)?.colorPalette"
                      :key="color"
                      class="palette-swatch"
                    >
                      <span class="palette-color" :style="{ backgroundColor: color }" />
                      <span class="text-caption">{{ color }}</span>
                    </div>
                  </div>
                </div>

                <v-row>
                  <v-col cols="12" sm="6">
                    <div class="text-subtitle-2 mb-2">Strengths</div>
                    <v-list density="compact" v-if="parsedAnalysisForAsset(activeAsset.id)?.strengths?.length">
                      <v-list-item v-for="strength in parsedAnalysisForAsset(activeAsset.id)?.strengths" :key="strength">
                        <template #prepend><v-icon color="success" size="16">mdi-check-circle</v-icon></template>
                        <v-list-item-title class="text-body-2">{{ strength }}</v-list-item-title>
                      </v-list-item>
                    </v-list>
                  </v-col>
                  <v-col cols="12" sm="6">
                    <div class="text-subtitle-2 mb-2">Improvements</div>
                    <v-list density="compact" v-if="parsedAnalysisForAsset(activeAsset.id)?.improvements?.length">
                      <v-list-item v-for="improvement in parsedAnalysisForAsset(activeAsset.id)?.improvements" :key="improvement">
                        <template #prepend><v-icon color="warning" size="16">mdi-arrow-up-circle</v-icon></template>
                        <v-list-item-title class="text-body-2">{{ improvement }}</v-list-item-title>
                      </v-list-item>
                    </v-list>
                  </v-col>
                </v-row>

                <v-row class="mt-2">
                  <v-col cols="12" sm="6">
                    <div class="text-subtitle-2 mb-1">Composition</div>
                    <div class="text-body-2 text-medium-emphasis">{{ parsedAnalysisForAsset(activeAsset.id)?.composition || '—' }}</div>
                  </v-col>
                  <v-col cols="12" sm="6">
                    <div class="text-subtitle-2 mb-1">Text readability</div>
                    <div class="text-body-2 text-medium-emphasis">{{ parsedAnalysisForAsset(activeAsset.id)?.textReadability || '—' }}</div>
                  </v-col>
                </v-row>
              </template>

              <v-alert v-else type="info" variant="tonal" class="mb-4">
                No analysis yet. Run `Analyze` to generate an AI summary for this creative.
              </v-alert>

              <v-divider class="my-4" />

              <div class="d-flex align-center mb-3">
                <div class="text-subtitle-1 font-weight-medium">Copy variants</div>
                <v-spacer />
                <v-progress-circular
                  v-if="store.copyVariantsLoadingByAsset[activeAsset.id]"
                  indeterminate
                  size="18"
                  width="2"
                  color="primary"
                />
              </div>

              <v-expansion-panels v-if="(store.copyVariantsByAsset[activeAsset.id] ?? []).length" variant="accordion">
                <v-expansion-panel
                  v-for="variant in store.copyVariantsByAsset[activeAsset.id]"
                  :key="variant.id"
                >
                  <v-expansion-panel-title>
                    <div class="d-flex align-center flex-wrap ga-2 w-100 pr-2">
                      <span class="font-weight-medium">{{ variant.headline || 'Untitled variant' }}</span>
                      <v-chip :color="copyVariantStatusColor(variant.status)" size="x-small">{{ variant.status }}</v-chip>
                      <v-chip size="x-small" variant="outlined">{{ variant.cta }}</v-chip>
                    </div>
                  </v-expansion-panel-title>
                  <v-expansion-panel-text>
                    <div class="text-caption text-medium-emphasis mb-2">
                      {{ formatDate(variant.createdAt) }} · {{ variant.language.toUpperCase() }}
                    </div>
                    <div class="mb-3">
                      <div class="text-subtitle-2">Primary text</div>
                      <div class="text-body-2 copy-block">{{ variant.primaryText || '—' }}</div>
                    </div>
                    <div class="mb-3">
                      <div class="text-subtitle-2">Description</div>
                      <div class="text-body-2 copy-block">{{ variant.description || '—' }}</div>
                    </div>
                    <div class="d-flex ga-2 flex-wrap">
                      <v-btn
                        size="small"
                        color="success"
                        variant="tonal"
                        :disabled="variant.status === 'APPROVED'"
                        :loading="store.copyVariantActionLoading[variant.id]"
                        @click="updateVariantStatus(variant.id, 'APPROVED')"
                      >
                        Approve
                      </v-btn>
                      <v-btn
                        size="small"
                        color="error"
                        variant="tonal"
                        :disabled="variant.status === 'REJECTED'"
                        :loading="store.copyVariantActionLoading[variant.id]"
                        @click="updateVariantStatus(variant.id, 'REJECTED')"
                      >
                        Reject
                      </v-btn>
                    </div>
                  </v-expansion-panel-text>
                </v-expansion-panel>
              </v-expansion-panels>

              <v-alert v-else type="info" variant="tonal">
                No copy variants yet. Generate copy after analysis to review AI suggestions.
              </v-alert>
            </v-col>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreativeDetail = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="3000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount, onMounted, nextTick } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import type { CreativeAnalysis, CreativeAsset } from '@/stores/creatives'
import { useCreativeStore } from '@/stores/creatives'
import { useClientStore } from '@/stores/clients'
import api from '@/api/client'
import PackageBuilder from '@/components/PackageBuilder.vue'

const store = useCreativeStore()
const clientStore = useClientStore()
const selectedClient = ref<string | null>(null)
const tab = ref('assets')
const showCreativeDetail = ref(false)
const activeAsset = ref<CreativeAsset | null>(null)
const isDragging = ref(false)
const snackbar = ref({ show: false, text: '', color: 'success' })
const previewUrls = ref<Record<string, string>>({})

type ParsedAnalysis = {
  overallSummary: string
  composition: string
  textReadability: string
  brandConsistency: string
  colorPalette: string[]
  strengths: string[]
  improvements: string[]
  platformFit: Record<string, string>
}

const assetElements = new Map<string, Element>()
let assetObserver: IntersectionObserver | null = null

interface UploadItem {
  id: string
  fileName: string
  size: number
  status: 'pending' | 'initiating' | 'uploading' | 'completing' | 'done' | 'error'
  progress: number
}
const uploadQueue = ref<UploadItem[]>([])

function assetStatusColor(status: string) {
  const map: Record<string, string> = { UPLOADING: 'info', READY: 'success', ANALYZING: 'warning', FAILED: 'error' }
  return map[status] || 'grey'
}

function formatSize(bytes: number) {
  if (bytes == null) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString() : '—'
}

function qualityScoreColor(score: number) {
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'error'
}

function copyVariantStatusColor(status: string) {
  const map: Record<string, string> = { DRAFT: 'grey', APPROVED: 'success', REJECTED: 'error' }
  return map[status] || 'grey'
}

function analysisForAsset(assetId: string) {
  return store.analysisByAsset[assetId] ?? null
}

function parseAnalysisRecord(analysis: CreativeAnalysis | null | undefined): ParsedAnalysis | null {
  if (!analysis?.analysisJson) return null
  try {
    const parsed = JSON.parse(analysis.analysisJson)
    return {
      overallSummary: parsed.overall_summary ?? '',
      composition: parsed.composition ?? '',
      textReadability: parsed.text_readability ?? '',
      brandConsistency: parsed.brand_consistency ?? '',
      colorPalette: Array.isArray(parsed.color_palette) ? parsed.color_palette : [],
      strengths: Array.isArray(parsed.strengths) ? parsed.strengths : [],
      improvements: Array.isArray(parsed.improvements) ? parsed.improvements : [],
      platformFit: parsed.platform_fit && typeof parsed.platform_fit === 'object' ? parsed.platform_fit : {},
    }
  } catch {
    return null
  }
}

function parsedAnalysisForAsset(assetId: string) {
  return parseAnalysisRecord(analysisForAsset(assetId))
}

function platformLabel(key: string) {
  return key
    .replace(/_/g, ' ')
    .replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function extractPlatformRating(value: string) {
  const match = value?.match(/(10|[1-9])(\s*\/\s*10)?/)
  if (!match) return 0
  const numeric = Number(match[1])
  return Math.max(0, Math.min(5, Math.round(numeric / 2)))
}

function platformFitEntries(assetId: string) {
  const analysis = parsedAnalysisForAsset(assetId)
  if (!analysis) return []
  return Object.entries(analysis.platformFit).map(([key, value]) => ({
    key,
    label: platformLabel(key),
    rating: extractPlatformRating(String(value)),
    description: String(value),
  }))
}

function previewUrlForAsset(assetId: string) {
  return previewUrls.value[assetId] || ''
}

async function loadPreviewUrl(asset: CreativeAsset) {
  if (previewUrls.value[asset.id] || asset.status !== 'READY') return
  try {
    const res = await api.get(`/creatives/${asset.id}/url`)
    previewUrls.value[asset.id] = res.data.url
    if (asset.assetType === 'IMAGE') {
      asset.thumbnailUrl = res.data.url
    }
  } catch {
    // ignore preview errors for cards
  }
}

async function ensureAssetInsights(assetId: string) {
  await Promise.allSettled([
    store.fetchAnalysis(assetId),
    store.fetchCopyVariants(assetId),
  ])
}

function setupAssetObserver() {
  assetObserver?.disconnect()
  assetObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return
      const assetId = entry.target.getAttribute('data-asset-id')
      if (assetId) {
        ensureAssetInsights(assetId)
      }
      assetObserver?.unobserve(entry.target)
    })
  }, { threshold: 0.35 })

  assetElements.forEach((element, assetId) => {
    element.setAttribute('data-asset-id', assetId)
    assetObserver?.observe(element)
  })
}

function setAssetCardRef(assetId: string) {
  return (element: Element | ComponentPublicInstance | null) => {
    const target = element instanceof Element ? element : null
    if (target) {
      assetElements.set(assetId, target)
      target.setAttribute('data-asset-id', assetId)
      assetObserver?.observe(target)
      return
    }
    assetElements.delete(assetId)
  }
}

async function onClientChange(clientId: string | null) {
  if (!clientId) return
  store.resetInsightState()
  previewUrls.value = {}
  assetElements.clear()
  await Promise.all([store.fetchAssets(clientId), store.fetchPackages(clientId)])
  await Promise.all(store.assets.filter((asset) => asset.assetType === 'IMAGE' && asset.status === 'READY').map(loadPreviewUrl))
  await nextTick()
  setupAssetObserver()
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
    previewUrls.value[asset.id] = res.data.url
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

async function openAssetDetail(asset: CreativeAsset) {
  activeAsset.value = asset
  showCreativeDetail.value = true
  await Promise.allSettled([ensureAssetInsights(asset.id), loadPreviewUrl(asset)])
}

async function analyzeAsset(asset: CreativeAsset) {
  try {
    await store.analyzeAsset(asset.id)
    snackbar.value = { show: true, text: 'Analysis updated', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Analysis failed', color: 'error' }
  }
}

async function generateCopyForAsset(asset: CreativeAsset) {
  try {
    if (!analysisForAsset(asset.id) && !store.analysisLoadingByAsset[asset.id]) {
      await store.analyzeAsset(asset.id)
    }
    await store.generateCopy(asset.id)
    snackbar.value = { show: true, text: 'Copy variants generated', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Copy generation failed', color: 'error' }
  }
}

async function updateVariantStatus(variantId: string, status: 'APPROVED' | 'REJECTED') {
  try {
    await store.updateCopyVariantStatus(variantId, status)
    snackbar.value = {
      show: true,
      text: status === 'APPROVED' ? 'Copy variant approved' : 'Copy variant rejected',
      color: 'success',
    }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Status update failed', color: 'error' }
  }
}

onMounted(() => clientStore.fetchClients())
onBeforeUnmount(() => assetObserver?.disconnect())
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
.analysis-score-chip {
  position: absolute;
  top: 12px;
  right: 12px;
}
.min-summary-row {
  min-height: 22px;
}
.platform-pill {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(25, 118, 210, 0.08);
}
.detail-preview {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  overflow: hidden;
}
.detail-preview-media {
  width: 100%;
  height: 100%;
  max-height: 420px;
  object-fit: contain;
}
.palette-swatch {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.03);
}
.palette-color {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.1);
}
.copy-block {
  white-space: pre-wrap;
}
</style>
