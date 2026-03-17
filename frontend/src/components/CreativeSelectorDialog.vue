<template>
  <v-dialog v-model="dialogVisible" max-width="900" scrollable>
    <v-card>
      <v-card-title class="d-flex align-center ga-2">
        <v-icon>mdi-image-multiple</v-icon>
        <span>Select Creative</span>
        <v-spacer />
        <v-btn icon size="small" @click="dialogVisible = false"><v-icon>mdi-close</v-icon></v-btn>
      </v-card-title>

      <v-divider />

      <v-card-text style="min-height: 400px">
        <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

        <v-alert v-if="!loading && readyAssets.length === 0" type="info" variant="tonal">
          No ready creatives found for this client. Upload creatives first.
        </v-alert>

        <v-row v-else>
          <v-col v-for="asset in readyAssets" :key="asset.id" cols="6" sm="4" md="3">
            <v-card
              variant="outlined"
              class="selector-card"
              :class="{ 'selector-card-selected': selectedId === asset.id }"
              @click="onSelect(asset)"
            >
              <div class="selector-thumb">
                <img v-if="previewUrls[asset.id]" :src="previewUrls[asset.id]" class="selector-thumb-img" />
                <v-icon v-else-if="asset.assetType === 'VIDEO'" size="40" color="grey">mdi-video</v-icon>
                <v-icon v-else size="40" color="grey">mdi-image</v-icon>
                <v-chip
                  v-if="copyVariantCounts[asset.id]"
                  class="selector-badge"
                  color="secondary"
                  size="x-small"
                >
                  {{ copyVariantCounts[asset.id] }} copy
                </v-chip>
              </div>
              <v-card-text class="pa-2">
                <div class="text-body-2 text-truncate">{{ asset.originalFilename }}</div>
                <div class="text-caption text-medium-emphasis">
                  {{ asset.assetType }}
                  <span v-if="asset.widthPx"> · {{ asset.widthPx }}×{{ asset.heightPx }}</span>
                </div>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>
      </v-card-text>

      <v-divider />

      <v-card-actions>
        <v-spacer />
        <v-btn @click="dialogVisible = false">Cancel</v-btn>
        <v-btn color="primary" :disabled="!selectedId" @click="onConfirm">
          Select
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import api from '@/api/client'
import type { CreativeAsset } from '@/stores/creatives'

const props = defineProps<{
  modelValue: boolean
  clientId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'selected', asset: { id: string; filename: string; thumbnailUrl: string | null; assetType: string }): void
}>()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const assets = ref<CreativeAsset[]>([])
const loading = ref(false)
const selectedId = ref<string | null>(null)
const previewUrls = ref<Record<string, string>>({})
const copyVariantCounts = ref<Record<string, number>>({})

const readyAssets = computed(() => assets.value.filter((a) => a.status === 'READY'))

watch(() => [props.modelValue, props.clientId], async ([visible, clientId]) => {
  if (visible && clientId) {
    selectedId.value = null
    await loadAssets(clientId as string)
  }
})

async function loadAssets(clientId: string) {
  loading.value = true
  try {
    const { data } = await api.get(`/clients/${clientId}/creatives`)
    assets.value = data

    // Load previews and copy counts in parallel
    await Promise.allSettled(
      data
        .filter((a: CreativeAsset) => a.status === 'READY')
        .map(async (asset: CreativeAsset) => {
          try {
            const [urlRes, copyRes] = await Promise.all([
              api.get(`/creatives/${asset.id}/url`),
              api.get(`/creatives/${asset.id}/copy-variants`),
            ])
            previewUrls.value[asset.id] = urlRes.data.url
            copyVariantCounts.value[asset.id] = copyRes.data.length
          } catch {
            // ignore
          }
        })
    )
  } finally {
    loading.value = false
  }
}

function onSelect(asset: CreativeAsset) {
  selectedId.value = asset.id
}

function onConfirm() {
  const asset = assets.value.find((a) => a.id === selectedId.value)
  if (asset) {
    emit('selected', {
      id: asset.id,
      filename: asset.originalFilename,
      thumbnailUrl: previewUrls.value[asset.id] || null,
      assetType: asset.assetType,
    })
    dialogVisible.value = false
  }
}
</script>

<style scoped>
.selector-card {
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.selector-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}
.selector-card-selected {
  border-color: #1976d2 !important;
  box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.3);
}
.selector-thumb {
  position: relative;
  height: 140px;
  background: #f5f5f5;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}
.selector-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.selector-badge {
  position: absolute;
  top: 6px;
  right: 6px;
}
</style>
