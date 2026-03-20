<template>
  <div>
    <!-- Package List View -->
    <template v-if="!editingPackage">
      <div class="d-flex justify-space-between align-center mb-4">
        <div class="text-h6">Creative Packages</div>
        <v-btn color="primary" @click="startNewPackage">
          <v-icon start>mdi-plus</v-icon> New Package
        </v-btn>
      </div>

      <v-progress-linear v-if="store.loading" indeterminate color="primary" class="mb-4" />

      <v-row v-if="store.packages.length > 0">
        <v-col v-for="pkg in store.packages" :key="pkg.id" cols="12" sm="6" md="4">
          <v-card variant="outlined" class="pkg-card" @click="openPackage(pkg)">
            <v-card-text>
              <div class="d-flex align-center ga-2 mb-2">
                <v-icon size="20" color="primary">mdi-package-variant</v-icon>
                <span class="text-subtitle-1 font-weight-medium text-truncate">{{ pkg.name }}</span>
              </div>
              <div class="d-flex flex-wrap ga-2 mb-2">
                <v-chip :color="packageStatusColor(pkg.status)" size="small">{{ pkg.status }}</v-chip>
                <v-chip v-if="pkg.objective" size="small" variant="outlined">{{ pkg.objective }}</v-chip>
              </div>
              <div class="text-caption text-medium-emphasis">
                Created {{ formatDate(pkg.createdAt) }}
              </div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <v-alert v-else type="info" variant="tonal">
        No packages yet. Create one to combine creatives with copy variants.
      </v-alert>
    </template>

    <!-- Package Editor View -->
    <template v-else>
      <div class="d-flex align-center ga-3 mb-4">
        <v-btn icon variant="text" @click="closeEditor"><v-icon>mdi-arrow-left</v-icon></v-btn>
        <div class="text-h6">{{ isNewPackage ? 'New Package' : editingPackage.name }}</div>
        <v-chip v-if="editingPackage.status" :color="packageStatusColor(editingPackage.status)" size="small">
          {{ editingPackage.status }}
        </v-chip>
        <v-spacer />
        <div class="text-body-2 text-medium-emphasis">
          {{ packageItems.length }} item{{ packageItems.length !== 1 ? 's' : '' }} in package
        </div>
      </div>

      <v-row>
        <!-- Left Panel: Available Assets -->
        <v-col cols="12" md="5">
          <v-card variant="outlined">
            <v-card-title class="d-flex align-center ga-2">
              <v-icon size="18">mdi-image-multiple</v-icon>
              <span>Available Assets</span>
            </v-card-title>
            <v-card-text>
              <!-- Package meta fields -->
              <v-text-field
                v-model="editingPackage.name"
                label="Package Name"
                variant="outlined"
                density="compact"
                :disabled="!isEditable"
                class="mb-3"
              />
              <v-select
                v-model="editingPackage.objective"
                :items="['SALES', 'LEADS', 'TRAFFIC', 'AWARENESS', 'ENGAGEMENT']"
                label="Objective"
                variant="outlined"
                density="compact"
                :disabled="!isEditable"
                clearable
                class="mb-3"
              />

              <!-- Filter bar -->
              <div class="d-flex ga-2 mb-3">
                <v-btn-toggle v-model="assetTypeFilter" density="compact" mandatory>
                  <v-btn value="all" size="small">All</v-btn>
                  <v-btn value="IMAGE" size="small">Images</v-btn>
                  <v-btn value="VIDEO" size="small">Videos</v-btn>
                </v-btn-toggle>
              </div>

              <v-progress-linear v-if="loadingAssets" indeterminate color="primary" class="mb-3" />

              <!-- Asset list with expandable variants -->
              <div v-if="filteredAssets.length > 0" class="asset-list">
                <div v-for="asset in filteredAssets" :key="asset.id" class="asset-row mb-2">
                  <v-card
                    variant="flat"
                    :color="expandedAsset === asset.id ? 'blue-grey-lighten-5' : undefined"
                    class="asset-card"
                    @click="toggleAssetExpansion(asset.id)"
                  >
                    <div class="d-flex align-center pa-2 ga-3">
                      <div class="asset-thumb">
                        <img v-if="asset.thumbnailUrl" :src="asset.thumbnailUrl" class="asset-thumb-img" />
                        <v-icon v-else-if="asset.assetType === 'VIDEO'" size="28" color="grey">mdi-video</v-icon>
                        <v-icon v-else size="28" color="grey">mdi-image</v-icon>
                      </div>
                      <div class="flex-grow-1 min-width-0">
                        <div class="text-body-2 text-truncate font-weight-medium">{{ asset.originalFilename }}</div>
                        <div class="d-flex ga-1 flex-wrap">
                          <v-chip v-if="asset.qualityScore != null" :color="qualityColor(asset.qualityScore)" size="x-small">
                            AI {{ Math.round(asset.qualityScore) }}
                          </v-chip>
                          <v-chip size="x-small" variant="outlined">{{ asset.copyVariants.length }} variants</v-chip>
                        </div>
                      </div>
                      <v-icon size="18">{{ expandedAsset === asset.id ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
                    </div>
                  </v-card>

                  <!-- Expanded copy variants -->
                  <v-expand-transition>
                    <div v-if="expandedAsset === asset.id" class="pl-4 pt-1">
                      <div v-if="asset.copyVariants.length === 0" class="text-caption text-medium-emphasis pa-2">
                        No copy variants available for this creative.
                      </div>
                      <div
                        v-for="variant in asset.copyVariants"
                        :key="variant.id"
                        class="variant-row d-flex align-center ga-2 pa-2 rounded mb-1"
                      >
                        <div class="flex-grow-1 min-width-0">
                          <div class="text-body-2 text-truncate">{{ variant.headline || 'Untitled' }}</div>
                          <div class="text-caption text-medium-emphasis text-truncate">{{ variant.primaryText }}</div>
                          <div class="d-flex ga-1 mt-1">
                            <v-chip size="x-small" :color="variantStatusColor(variant.status)">{{ variant.status }}</v-chip>
                            <v-chip size="x-small" variant="outlined">{{ variant.cta }}</v-chip>
                          </div>
                        </div>
                        <v-btn
                          v-if="isEditable"
                          icon
                          size="small"
                          color="primary"
                          variant="tonal"
                          :disabled="isItemAlreadyAdded(asset.id, variant.id)"
                          @click.stop="addItem(asset, variant)"
                          :title="isItemAlreadyAdded(asset.id, variant.id) ? 'Already added' : 'Add to package'"
                        >
                          <v-icon size="18">{{ isItemAlreadyAdded(asset.id, variant.id) ? 'mdi-check' : 'mdi-plus' }}</v-icon>
                        </v-btn>
                      </div>
                    </div>
                  </v-expand-transition>
                </div>
              </div>
              <v-alert v-else type="info" variant="tonal" class="mt-2">
                No ready creatives found. Upload assets first.
              </v-alert>
            </v-card-text>
          </v-card>
        </v-col>

        <!-- Right Panel: Package Contents -->
        <v-col cols="12" md="7">
          <v-card variant="outlined" class="h-100">
            <v-card-title class="d-flex align-center ga-2">
              <v-icon size="18">mdi-package-variant</v-icon>
              <span>Package Contents</span>
            </v-card-title>
            <v-card-text>
              <v-progress-linear v-if="loadingItems" indeterminate color="primary" class="mb-3" />

              <div v-if="packageItems.length > 0">
                <div v-for="item in packageItems" :key="item.id" class="package-item d-flex align-center ga-3 pa-3 rounded mb-2">
                  <!-- Creative thumbnail -->
                  <div class="item-thumb">
                    <img v-if="item.creativeAsset?.thumbnailUrl" :src="item.creativeAsset.thumbnailUrl" class="item-thumb-img" />
                    <v-icon v-else-if="item.creativeAsset?.assetType === 'VIDEO'" size="28" color="grey">mdi-video</v-icon>
                    <v-icon v-else size="28" color="grey">mdi-image</v-icon>
                  </div>

                  <!-- Copy variant text -->
                  <div class="flex-grow-1 min-width-0">
                    <div class="text-body-2 font-weight-medium text-truncate">
                      {{ item.copyVariant?.headline || 'No headline' }}
                    </div>
                    <div class="text-caption text-medium-emphasis text-truncate">
                      {{ item.copyVariant?.primaryText || 'No primary text' }}
                    </div>
                    <div class="d-flex ga-1 mt-1">
                      <v-chip size="x-small" variant="outlined">{{ item.ctaType || item.copyVariant?.ctaType || 'N/A' }}</v-chip>
                      <v-chip v-if="item.qualityScore != null" :color="qualityColor(item.qualityScore)" size="x-small">
                        AI {{ Math.round(item.qualityScore) }}
                      </v-chip>
                      <v-chip size="x-small" color="grey" variant="tonal">
                        {{ item.creativeAsset?.originalFilename || 'Unknown asset' }}
                      </v-chip>
                    </div>
                  </div>

                  <!-- Actions -->
                  <div class="d-flex ga-1">
                    <v-btn icon size="small" variant="text" @click="previewItem(item)" title="Preview">
                      <v-icon size="18">mdi-eye</v-icon>
                    </v-btn>
                    <v-btn
                      v-if="isEditable"
                      icon
                      size="small"
                      variant="text"
                      color="error"
                      @click="removeItem(item.id)"
                      title="Remove"
                    >
                      <v-icon size="18">mdi-close</v-icon>
                    </v-btn>
                  </div>
                </div>
              </div>

              <div v-else class="text-center pa-8">
                <v-icon size="48" color="grey-lighten-1" class="mb-3">mdi-package-variant-plus</v-icon>
                <div class="text-body-1 text-medium-emphasis">
                  Click <v-icon size="16">mdi-plus</v-icon> on a copy variant to add items to this package.
                </div>
              </div>
            </v-card-text>

            <!-- Bottom Actions -->
            <v-card-actions class="pa-4 pt-0 flex-wrap ga-2">
              <v-btn
                v-if="isEditable"
                variant="outlined"
                :loading="saving"
                @click="saveDraft"
              >
                <v-icon start>mdi-content-save-outline</v-icon> Save Draft
              </v-btn>
              <v-btn
                v-if="editingPackage.status === 'DRAFT' && packageItems.length > 0"
                color="info"
                variant="tonal"
                :loading="submitting"
                @click="submitForReview"
              >
                <v-icon start>mdi-send</v-icon> Submit for Review
              </v-btn>
              <v-btn
                v-if="editingPackage.status === 'IN_REVIEW'"
                color="success"
                variant="tonal"
                :loading="approving"
                @click="approve"
              >
                <v-icon start>mdi-check</v-icon> Approve
              </v-btn>
              <v-btn
                v-if="editingPackage.status === 'IN_REVIEW'"
                color="error"
                variant="tonal"
                :loading="rejecting"
                @click="reject"
              >
                <v-icon start>mdi-undo</v-icon> Reject
              </v-btn>
              <v-spacer />
              <v-btn variant="text" @click="closeEditor">Back to Packages</v-btn>
            </v-card-actions>
          </v-card>
        </v-col>
      </v-row>
    </template>

    <!-- Ad Preview Dialog -->
    <v-dialog v-model="showPreview" max-width="480" scrollable>
      <v-card v-if="previewingItem">
        <v-card-title class="d-flex align-center ga-2">
          <v-icon size="18">mdi-cellphone</v-icon>
          <span>Ad Preview</span>
          <v-spacer />
          <v-btn icon size="small" @click="showPreview = false"><v-icon>mdi-close</v-icon></v-btn>
        </v-card-title>
        <v-divider />
        <v-card-text class="pa-0">
          <div class="ad-mockup">
            <div class="ad-mockup-image">
              <img v-if="previewingItem.creativeAsset?.thumbnailUrl" :src="previewingItem.creativeAsset.thumbnailUrl" class="ad-mockup-img" />
              <div v-else class="ad-mockup-placeholder">
                <v-icon size="48" color="grey">mdi-image-off-outline</v-icon>
              </div>
            </div>
            <div class="ad-mockup-content pa-4">
              <div class="text-body-2 mb-3" style="white-space: pre-wrap;">{{ previewingItem.copyVariant?.primaryText || 'Primary text' }}</div>
              <div class="text-subtitle-2 font-weight-bold mb-1">{{ previewingItem.copyVariant?.headline || 'Headline' }}</div>
              <div class="text-caption text-medium-emphasis mb-3">{{ previewingItem.copyVariant?.description || 'Description' }}</div>
              <v-btn size="small" color="primary" variant="flat" disabled class="mb-2">
                {{ previewingItem.ctaType || previewingItem.copyVariant?.ctaType || 'LEARN_MORE' }}
              </v-btn>
              <div v-if="previewingItem.destinationUrl" class="text-caption text-medium-emphasis text-truncate">
                {{ previewingItem.destinationUrl }}
              </div>
            </div>
          </div>
        </v-card-text>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="3000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useCreativeStore } from '@/stores/creatives'
import type { CreativeWithVariants, VariantSummary, PackageItemDetail, CreativePackage } from '@/stores/creatives'

const props = defineProps<{
  clientId: string | null
}>()

const store = useCreativeStore()

const editingPackage = ref<{
  id: string | null
  name: string
  objective: string | null
  status: string
  notes: string | null
} | null>(null)

const isNewPackage = ref(false)
const packageItems = ref<PackageItemDetail[]>([])
const creativesWithVariants = ref<CreativeWithVariants[]>([])
const expandedAsset = ref<string | null>(null)
const assetTypeFilter = ref('all')
const loadingAssets = ref(false)
const loadingItems = ref(false)
const saving = ref(false)
const submitting = ref(false)
const approving = ref(false)
const rejecting = ref(false)
const showPreview = ref(false)
const previewingItem = ref<PackageItemDetail | null>(null)
const snackbar = ref({ show: false, text: '', color: 'success' as string })

const isEditable = computed(() => {
  if (!editingPackage.value) return false
  return editingPackage.value.status === 'DRAFT' || isNewPackage.value
})

const filteredAssets = computed(() => {
  let list = creativesWithVariants.value
  if (assetTypeFilter.value !== 'all') {
    list = list.filter(a => a.assetType === assetTypeFilter.value)
  }
  return list
})

function packageStatusColor(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'grey', IN_REVIEW: 'info', APPROVED: 'success',
    SCHEDULED: 'warning', USED: 'primary', ARCHIVED: 'error',
  }
  return map[status] || 'grey'
}

function variantStatusColor(status: string) {
  const map: Record<string, string> = { DRAFT: 'grey', APPROVED: 'success', REJECTED: 'error' }
  return map[status] || 'grey'
}

function qualityColor(score: number) {
  if (score >= 80) return 'success'
  if (score >= 60) return 'warning'
  return 'error'
}

function formatDate(value: string | null | undefined) {
  return value ? new Date(value).toLocaleDateString() : '—'
}

function isItemAlreadyAdded(assetId: string, variantId: string): boolean {
  return packageItems.value.some(
    item => item.creativeAsset?.id === assetId && item.copyVariant?.id === variantId
  )
}

function toggleAssetExpansion(assetId: string) {
  expandedAsset.value = expandedAsset.value === assetId ? null : assetId
}

// ── Load data ──

watch(() => props.clientId, async (clientId) => {
  if (clientId) {
    await store.fetchPackages(clientId)
  }
}, { immediate: true })

async function loadAssetsWithVariants() {
  if (!props.clientId) return
  loadingAssets.value = true
  try {
    creativesWithVariants.value = await store.fetchCreativesWithVariants(props.clientId)
  } catch (e) {
    console.error('Failed to load creatives with variants:', e)
  } finally {
    loadingAssets.value = false
  }
}

async function loadPackageItems(packageId: string) {
  loadingItems.value = true
  try {
    const detail = await store.fetchPackageDetail(packageId)
    packageItems.value = detail.items
  } catch (e) {
    console.error('Failed to load package items:', e)
    packageItems.value = []
  } finally {
    loadingItems.value = false
  }
}

// ── Package CRUD ──

function startNewPackage() {
  isNewPackage.value = true
  editingPackage.value = {
    id: null,
    name: '',
    objective: null,
    status: 'DRAFT',
    notes: null,
  }
  packageItems.value = []
  loadAssetsWithVariants()
}

async function openPackage(pkg: CreativePackage) {
  isNewPackage.value = false
  editingPackage.value = {
    id: pkg.id,
    name: pkg.name,
    objective: pkg.objective,
    status: pkg.status,
    notes: pkg.notes,
  }
  await Promise.all([loadAssetsWithVariants(), loadPackageItems(pkg.id)])
}

function closeEditor() {
  editingPackage.value = null
  isNewPackage.value = false
  packageItems.value = []
  expandedAsset.value = null
}

async function saveDraft() {
  if (!editingPackage.value || !props.clientId) return
  saving.value = true
  try {
    if (isNewPackage.value) {
      const created = await store.createPackage(props.clientId, {
        name: editingPackage.value.name,
        objective: editingPackage.value.objective || undefined,
      })
      editingPackage.value.id = created.id
      editingPackage.value.status = created.status
      isNewPackage.value = false
      snackbar.value = { show: true, text: 'Package created!', color: 'success' }
    } else if (editingPackage.value.id) {
      await store.updatePackage(editingPackage.value.id, {
        name: editingPackage.value.name,
        objective: editingPackage.value.objective || undefined,
        notes: editingPackage.value.notes || undefined,
      })
      snackbar.value = { show: true, text: 'Package saved!', color: 'success' }
    }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Save failed', color: 'error' }
  } finally {
    saving.value = false
  }
}

async function addItem(asset: CreativeWithVariants, variant: VariantSummary) {
  if (!editingPackage.value) return

  // Auto-save new package first
  if (isNewPackage.value) {
    if (!editingPackage.value.name.trim()) {
      snackbar.value = { show: true, text: 'Enter a package name first', color: 'warning' }
      return
    }
    await saveDraft()
  }

  if (!editingPackage.value.id) return

  try {
    await store.addPackageItem(editingPackage.value.id, {
      creativeAssetId: asset.id,
      copyVariantId: variant.id,
      weight: 50,
    })
    // Reload items with enriched data
    await loadPackageItems(editingPackage.value.id)
    snackbar.value = { show: true, text: 'Item added!', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Failed to add item', color: 'error' }
  }
}

async function removeItem(itemId: string) {
  if (!editingPackage.value?.id) return
  try {
    await store.deletePackageItem(editingPackage.value.id, itemId)
    packageItems.value = packageItems.value.filter(i => i.id !== itemId)
    snackbar.value = { show: true, text: 'Item removed', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Remove failed', color: 'error' }
  }
}

function previewItem(item: PackageItemDetail) {
  previewingItem.value = item
  showPreview.value = true
}

// ── Status transitions ──

async function submitForReview() {
  if (!editingPackage.value?.id) return
  if (packageItems.value.length === 0) {
    snackbar.value = { show: true, text: 'Package must have at least 1 item to submit', color: 'warning' }
    return
  }
  submitting.value = true
  try {
    const updated = await store.submitPackage(editingPackage.value.id)
    editingPackage.value.status = updated.status
    snackbar.value = { show: true, text: 'Submitted for review!', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Submit failed', color: 'error' }
  } finally {
    submitting.value = false
  }
}

async function approve() {
  if (!editingPackage.value?.id) return
  approving.value = true
  try {
    const updated = await store.approvePackage(editingPackage.value.id)
    editingPackage.value.status = updated.status
    snackbar.value = { show: true, text: 'Package approved!', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Approve failed', color: 'error' }
  } finally {
    approving.value = false
  }
}

async function reject() {
  if (!editingPackage.value?.id) return
  rejecting.value = true
  try {
    const updated = await store.rejectPackage(editingPackage.value.id)
    editingPackage.value.status = updated.status
    snackbar.value = { show: true, text: 'Package rejected → back to DRAFT', color: 'info' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Reject failed', color: 'error' }
  } finally {
    rejecting.value = false
  }
}
</script>

<style scoped>
.pkg-card {
  cursor: pointer;
  transition: box-shadow 0.2s;
}
.pkg-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.asset-list {
  max-height: 600px;
  overflow-y: auto;
}

.asset-card {
  cursor: pointer;
  transition: background 0.15s;
}
.asset-card:hover {
  background: rgba(0, 0, 0, 0.04) !important;
}

.asset-thumb {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  background: #f5f5f5;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.asset-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.variant-row {
  transition: background 0.15s;
}
.variant-row:hover {
  background: rgba(0, 0, 0, 0.04);
}

.package-item {
  background: rgba(0, 0, 0, 0.02);
  transition: background 0.15s;
}
.package-item:hover {
  background: rgba(0, 0, 0, 0.06);
}

.item-thumb {
  width: 56px;
  height: 56px;
  border-radius: 8px;
  background: #f5f5f5;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.item-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.min-width-0 {
  min-width: 0;
}

/* Ad preview mockup */
.ad-mockup {
  background: #fff;
}
.ad-mockup-image {
  width: 100%;
  height: 280px;
  background: #f5f5f5;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ad-mockup-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.ad-mockup-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}
</style>
