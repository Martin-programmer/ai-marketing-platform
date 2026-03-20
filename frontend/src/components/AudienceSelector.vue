<template>
  <div>
    <v-combobox
      :model-value="selectedItems"
      :items="availableItems"
      :label="label"
      :hint="hint || 'Select from list or type audience name manually'"
      :loading="loading"
      :return-object="true"
      :multiple="true"
      :chips="false"
      item-title="display"
      item-value="id"
      variant="outlined"
      density="comfortable"
      :hide-selected="true"
      @update:model-value="onSelect"
    >
      <template #item="{ props, item }">
        <v-list-item v-bind="props">
          <template #title>{{ item.name }}</template>
          <template #subtitle>~{{ formatAudienceSize(item.approximate_count) }}</template>
        </v-list-item>
      </template>
      <template #append-inner>
        <v-progress-circular v-if="loading" indeterminate size="18" width="2" color="primary" />
      </template>
    </v-combobox>

    <div v-if="selectedItems.length" class="d-flex flex-wrap ga-2 mt-2">
      <v-chip
        v-for="audience in selectedItems"
        :key="audience.id"
        closable
        size="small"
        color="teal"
        variant="tonal"
        @click:close="removeAudience(audience.id)"
      >
        {{ audience.name }} · ~{{ formatAudienceSize(audience.approximate_count) }}
      </v-chip>
    </div>
    <div v-if="errorMessage" class="text-caption text-orange-darken-2 mt-1">{{ errorMessage }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import api from '@/api/client'
import type { MetaAudienceOption } from '@/types/metaTargeting'

const props = withDefaults(defineProps<{
  modelValue: MetaAudienceOption[]
  clientId: string
  label?: string
  hint?: string
}>(), {
  label: 'Custom Audiences',
  hint: '',
})

const emit = defineEmits<{
  'update:modelValue': [value: MetaAudienceOption[]]
}>()

const audiences = ref<MetaAudienceOption[]>([])
const loading = ref(false)
const errorMessage = ref('')
const selectedItems = computed(() => props.modelValue ?? [])
const availableItems = computed(() => dedupeById([...selectedItems.value, ...audiences.value]))

watch(() => props.clientId, () => {
  if (props.clientId) {
    void loadAudiences()
  }
})

onMounted(() => {
  if (props.clientId) {
    void loadAudiences()
  }
})

async function loadAudiences() {
  if (!props.clientId) {
    audiences.value = []
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await api.get(`/clients/${props.clientId}/meta/audiences`)
    audiences.value = (data as MetaAudienceOption[]).map((item) => ({
      ...item,
      display: `${item.name} (~${formatAudienceSize(item.approximate_count)})`,
    }))
  } catch {
    audiences.value = []
    errorMessage.value = 'Search unavailable'
  } finally {
    loading.value = false
  }
}

function onSelect(value: Array<MetaAudienceOption | string>) {
  emit('update:modelValue', dedupeById(value.map(normalizeAudience)))
}

function removeAudience(id: string) {
  emit('update:modelValue', selectedItems.value.filter((item) => item.id !== id))
}

function dedupeById(items: MetaAudienceOption[]) {
  const seen = new Set<string>()
  return items.filter((item) => {
    if (seen.has(item.id)) return false
    seen.add(item.id)
    return true
  })
}

function formatAudienceSize(size?: number) {
  if (!size) return '0'
  if (size >= 1_000_000) return `${(size / 1_000_000).toFixed(1)}M`
  if (size >= 1_000) return `${(size / 1_000).toFixed(0)}K`
  return String(size)
}

function normalizeAudience(item: MetaAudienceOption | string): MetaAudienceOption {
  if (typeof item === 'string') {
    const trimmed = item.trim()
    return {
      id: trimmed,
      name: trimmed,
      display: trimmed,
    }
  }
  return {
    ...item,
    id: item.id || item.name,
    name: item.name || item.id,
    display: item.display || item.name || item.id,
  }
}

</script>
