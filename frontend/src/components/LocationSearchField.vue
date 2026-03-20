<template>
  <div>
    <v-combobox
      :model-value="selectedItems"
      :items="availableItems"
      :label="label"
      :hint="hint || 'Type a location or search when Meta is connected'"
      :loading="loading"
      :return-object="true"
      :chips="false"
      :multiple="true"
      item-title="display"
      item-value="key"
      variant="outlined"
      density="comfortable"
      hide-no-data
      no-filter
      :search="search"
      :hide-selected="true"
      @update:search="onSearchChange"
      @update:model-value="onSelect"
    >
      <template #item="{ props, item }">
        <v-list-item v-bind="props">
          <template #prepend>
            <span class="text-body-2 mr-2">{{ flagEmoji(item.country_code) }}</span>
          </template>
          <template #title>{{ item.name }}</template>
          <template #subtitle>
            {{ item.country_name || '—' }}
          </template>
          <template #append>
            <v-chip size="x-small" variant="outlined">{{ item.type }}</v-chip>
          </template>
        </v-list-item>
      </template>
      <template #append-inner>
        <v-progress-circular v-if="loading" indeterminate size="18" width="2" color="primary" />
      </template>
    </v-combobox>

    <div v-if="selectedItems.length" class="d-flex flex-wrap ga-2 mt-2">
      <v-chip
        v-for="location in selectedItems"
        :key="`${location.type}-${location.key}`"
        closable
        size="small"
        color="primary"
        variant="tonal"
        @click:close="removeLocation(location.key, location.type)"
      >
        {{ flagEmoji(location.country_code) }} {{ location.name }} · {{ location.type }}
      </v-chip>
    </div>
    <div v-if="errorMessage" class="text-caption text-orange-darken-2 mt-1">{{ errorMessage }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import api from '@/api/client'
import type { MetaLocationOption } from '@/types/metaTargeting'

const props = withDefaults(defineProps<{
  modelValue: MetaLocationOption[]
  clientId: string
  label?: string
  hint?: string
  required?: boolean
}>(), {
  label: 'Locations',
  hint: '',
  required: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: MetaLocationOption[]]
}>()

const selectedItems = computed(() => props.modelValue ?? [])
const availableItems = computed(() => dedupe([...selectedItems.value, ...results.value]))

const loading = ref(false)
const search = ref('')
const results = ref<MetaLocationOption[]>([])
const errorMessage = ref('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(() => props.clientId, () => {
  results.value = []
  errorMessage.value = ''
  search.value = ''
})

function onSearchChange(value: string) {
  search.value = value
  errorMessage.value = ''

  if (searchTimer) clearTimeout(searchTimer)
  if (!value || value.trim().length < 2) {
    results.value = []
    return
  }

  searchTimer = setTimeout(() => {
    void searchLocations(value.trim())
  }, 300)
}

async function searchLocations(query: string) {
  loading.value = true
  try {
    const { data } = await api.get('/meta/locations/search', {
      params: { q: query, type: 'city,country,region', clientId: props.clientId },
    })
    results.value = (data as MetaLocationOption[]).slice(0, 25).map((item) => ({
      ...item,
      display: `${item.name}${item.country_name ? ', ' + item.country_name : ''} (${item.type})`,
    }))
  } catch {
    results.value = []
    errorMessage.value = 'Search unavailable'
  } finally {
    loading.value = false
  }
}

function onSelect(value: Array<MetaLocationOption | string>) {
  emit('update:modelValue', dedupe(value.map(normalizeLocation)))
}

function removeLocation(key: string, type: string) {
  emit('update:modelValue', selectedItems.value.filter((item) => !(item.key === key && item.type === type)))
}

function dedupe(items: MetaLocationOption[]) {
  const seen = new Set<string>()
  return items.filter((item) => {
    const composite = `${item.type}:${item.key}`
    if (seen.has(composite)) return false
    seen.add(composite)
    return true
  })
}

function flagEmoji(countryCode?: string) {
  if (!countryCode || countryCode.length !== 2) return '🌍'
  return countryCode
    .toUpperCase()
    .split('')
    .map((char) => String.fromCodePoint(127397 + char.charCodeAt(0)))
    .join('')
}

function normalizeLocation(item: MetaLocationOption | string): MetaLocationOption {
  if (typeof item === 'string') {
    const trimmed = item.trim()
    return {
      key: trimmed,
      name: trimmed,
      type: 'custom',
      display: trimmed,
    }
  }
  return {
    ...item,
    key: item.key || item.name,
    name: item.name || item.key,
    type: item.type || 'custom',
    display: item.display || item.name || item.key,
  }
}


</script>
