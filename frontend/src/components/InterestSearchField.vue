<template>
  <div>
    <v-combobox
      :model-value="selectedItems"
      :items="availableItems"
      :label="label"
      :hint="hint || 'Type an interest or search when Meta is connected'"
      :loading="loading"
      :return-object="true"
      :chips="false"
      :multiple="true"
      :closable-chips="false"
      item-title="display"
      item-value="id"
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
          <template #title>{{ item.name }}</template>
          <template #subtitle>
            {{ formatAudienceSize(item.audience_size) }} audience size
          </template>
        </v-list-item>
      </template>
      <template #append-inner>
        <v-progress-circular v-if="loading" indeterminate size="18" width="2" color="primary" />
      </template>
    </v-combobox>

    <div v-if="selectedItems.length" class="d-flex flex-wrap ga-2 mt-2">
      <v-chip
        v-for="interest in selectedItems"
        :key="interest.id"
        closable
        size="small"
        color="deep-purple"
        variant="tonal"
        @click:close="removeInterest(interest.id)"
      >
        {{ interest.name }} · {{ formatAudienceSize(interest.audience_size) }}
      </v-chip>
    </div>
    <div v-if="errorMessage" class="text-caption text-orange-darken-2 mt-1">{{ errorMessage }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import api from '@/api/client'
import type { MetaInterestOption } from '@/types/metaTargeting'

const props = withDefaults(defineProps<{
  modelValue: MetaInterestOption[]
  clientId: string
  label?: string
  hint?: string
  required?: boolean
}>(), {
  label: 'Interests',
  hint: '',
  required: false,
})

const emit = defineEmits<{
  'update:modelValue': [value: MetaInterestOption[]]
}>()

const selectedItems = computed(() => props.modelValue ?? [])
const availableItems = computed(() => dedupeById([...selectedItems.value, ...results.value]))

const loading = ref(false)
const search = ref('')
const results = ref<MetaInterestOption[]>([])
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
    void searchInterests(value.trim())
  }, 300)
}

async function searchInterests(query: string) {
  loading.value = true
  try {
    const { data } = await api.get('/meta/interests/search', { params: { q: query, clientId: props.clientId } })
    results.value = (data as MetaInterestOption[]).slice(0, 25).map((item) => ({
      ...item,
      display: `${item.name} (${formatAudienceSize(item.audience_size)})`,
    }))
  } catch {
    results.value = []
    errorMessage.value = 'Search unavailable'
  } finally {
    loading.value = false
  }
}

function onSelect(value: Array<MetaInterestOption | string>) {
  emit('update:modelValue', dedupeById(value.map(normalizeInterest)))
}

function removeInterest(id: string) {
  emit('update:modelValue', selectedItems.value.filter((item) => item.id !== id))
}

function dedupeById(items: MetaInterestOption[]) {
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

function normalizeInterest(item: MetaInterestOption | string): MetaInterestOption {
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
