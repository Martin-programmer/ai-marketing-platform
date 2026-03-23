<template>
  <v-container fluid>
    <div class="d-flex align-center mb-2">
      <div>
        <h1 class="text-h4">Platform Settings</h1>
        <p class="text-subtitle-1 text-medium-emphasis">
          Manage all system configuration. Changes apply immediately unless noted otherwise.
        </p>
      </div>
      <v-spacer />
      <v-btn
        variant="tonal"
        color="primary"
        prepend-icon="mdi-cached"
        :loading="clearingCache"
        @click="handleClearCache"
        class="mr-2"
      >
        Clear Cache
      </v-btn>
    </div>

    <v-alert v-if="error" type="error" closable class="mb-4" @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- Search -->
    <v-text-field
      v-model="search"
      prepend-inner-icon="mdi-magnify"
      label="Search settings..."
      variant="outlined"
      density="compact"
      clearable
      hide-details
      class="mb-4"
      style="max-width: 400px"
    />

    <!-- Loading skeleton -->
    <template v-if="loading">
      <v-skeleton-loader v-for="n in 3" :key="n" type="card" class="mb-4" />
    </template>

    <!-- Category tabs -->
    <template v-else>
      <v-tabs v-model="activeTab" color="primary" show-arrows>
        <v-tab v-for="cat in categories" :key="cat" :value="cat">{{ cat }}</v-tab>
      </v-tabs>

      <v-window v-model="activeTab">
        <v-window-item v-for="cat in categories" :key="cat" :value="cat">
          <v-card class="mt-4 pa-4">
            <!-- Category-specific banners -->
            <v-alert
              v-if="cat === 'SCHEDULE'"
              type="info"
              variant="tonal"
              class="mb-4"
              icon="mdi-information"
            >
              Changes to cron schedules require application restart to take effect.
            </v-alert>

            <v-alert
              v-if="cat === 'SECURITY'"
              type="warning"
              variant="tonal"
              class="mb-4"
              icon="mdi-shield-alert"
            >
              Changing security settings (token expiry, 2FA parameters) will affect all users.
              Proceed with caution.
            </v-alert>

            <!-- Settings list -->
            <div v-for="setting in filteredSettingsForCategory(cat)" :key="setting.settingKey" class="mb-6">
              <div class="d-flex align-center mb-1">
                <span class="text-subtitle-2 font-weight-bold">{{ setting.displayName }}</span>
                <v-spacer />
                <v-chip size="x-small" variant="outlined" class="ml-2">{{ setting.valueType }}</v-chip>
                <span v-if="setting.updatedAt" class="text-caption text-medium-emphasis ml-3">
                  Updated {{ formatDate(setting.updatedAt) }}
                </span>
              </div>

              <p class="text-caption text-medium-emphasis mb-2">{{ setting.description }}</p>

              <div class="d-flex align-center ga-2">
                <!-- Boolean: switch -->
                <v-switch
                  v-if="setting.valueType === 'BOOLEAN'"
                  v-model="editValues[setting.settingKey]"
                  :label="editValues[setting.settingKey] ? 'Enabled' : 'Disabled'"
                  color="primary"
                  hide-details
                  density="compact"
                  :disabled="!setting.editable"
                />

                <!-- JSON: textarea -->
                <v-textarea
                  v-else-if="setting.valueType === 'JSON'"
                  v-model="editValues[setting.settingKey]"
                  variant="outlined"
                  density="compact"
                  rows="3"
                  auto-grow
                  hide-details
                  :disabled="!setting.editable"
                  style="font-family: monospace; max-width: 600px"
                />

                <!-- INTEGER -->
                <v-text-field
                  v-else-if="setting.valueType === 'INTEGER'"
                  v-model="editValues[setting.settingKey]"
                  type="number"
                  variant="outlined"
                  density="compact"
                  hide-details
                  :disabled="!setting.editable"
                  style="max-width: 300px"
                />

                <!-- DECIMAL -->
                <v-text-field
                  v-else-if="setting.valueType === 'DECIMAL'"
                  v-model="editValues[setting.settingKey]"
                  type="number"
                  step="0.01"
                  variant="outlined"
                  density="compact"
                  hide-details
                  :disabled="!setting.editable"
                  style="max-width: 300px"
                />

                <!-- STRING (default) -->
                <v-text-field
                  v-else
                  v-model="editValues[setting.settingKey]"
                  variant="outlined"
                  density="compact"
                  hide-details
                  :disabled="!setting.editable"
                  style="max-width: 500px"
                />

                <!-- Save button per field -->
                <v-btn
                  v-if="setting.editable && isModified(setting)"
                  color="primary"
                  size="small"
                  variant="flat"
                  :loading="saving[setting.settingKey]"
                  @click="saveSetting(setting)"
                >
                  Save
                </v-btn>

                <!-- Reset button -->
                <v-btn
                  v-if="setting.editable && isModified(setting)"
                  size="small"
                  variant="text"
                  @click="resetSetting(setting)"
                >
                  Reset
                </v-btn>

                <v-chip v-if="!setting.editable" size="x-small" color="grey" variant="tonal">
                  Read-only
                </v-chip>
              </div>
            </div>

            <!-- Empty state for filtered -->
            <div
              v-if="filteredSettingsForCategory(cat).length === 0"
              class="text-center text-medium-emphasis py-8"
            >
              <v-icon size="48" color="grey-lighten-1">mdi-magnify</v-icon>
              <p class="mt-2">No settings match your search.</p>
            </div>
          </v-card>
        </v-window-item>
      </v-window>
    </template>

    <!-- Snackbar -->
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="3000" location="bottom end">
      {{ snackbar.text }}
    </v-snackbar>

    <!-- Unsaved changes guard dialog -->
    <v-dialog v-model="showUnsavedDialog" max-width="420" persistent>
      <v-card>
        <v-card-title>Unsaved Changes</v-card-title>
        <v-card-text>
          You have unsaved changes. Are you sure you want to leave?
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="cancelNavigation">Stay</v-btn>
          <v-btn color="error" variant="flat" @click="confirmNavigation">Leave</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive, onBeforeUnmount } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { settingsApi } from '@/api/adminConfig'

// ── Types ────────────────────────────────────────────────

interface SystemSetting {
  id: string
  category: string
  settingKey: string
  settingValue: string
  valueType: string
  displayName: string
  description: string
  editable: boolean
  updatedAt: string | null
  updatedBy: string | null
}

// ── State ────────────────────────────────────────────────

const loading = ref(true)
const error = ref<string | null>(null)
const search = ref('')
const activeTab = ref('')
const clearingCache = ref(false)

const settingsByCategory = ref<Record<string, SystemSetting[]>>({})
const editValues = reactive<Record<string, string | boolean>>({})
const originalValues = reactive<Record<string, string | boolean>>({})
const saving = reactive<Record<string, boolean>>({})

const snackbar = ref({ show: false, text: '', color: 'success' })

// Unsaved-changes guard
const showUnsavedDialog = ref(false)
let pendingNavigation: (() => void) | null = null

// ── Computed ─────────────────────────────────────────────

const categories = computed(() => Object.keys(settingsByCategory.value))

function filteredSettingsForCategory(cat: string): SystemSetting[] {
  const settings = settingsByCategory.value[cat] || []
  if (!search.value) return settings
  const q = search.value.toLowerCase()
  return settings.filter(
    s =>
      s.settingKey.toLowerCase().includes(q) ||
      s.displayName.toLowerCase().includes(q) ||
      s.description.toLowerCase().includes(q)
  )
}

const hasUnsavedChanges = computed(() => {
  return Object.keys(originalValues).some(key => {
    return String(editValues[key]) !== String(originalValues[key])
  })
})

// ── Methods ──────────────────────────────────────────────

function toEditValue(setting: SystemSetting): string | boolean {
  if (setting.valueType === 'BOOLEAN') {
    return setting.settingValue === 'true'
  }
  return setting.settingValue
}

function isModified(setting: SystemSetting): boolean {
  return String(editValues[setting.settingKey]) !== String(originalValues[setting.settingKey])
}

function resetSetting(setting: SystemSetting) {
  editValues[setting.settingKey] = originalValues[setting.settingKey]!
}

async function saveSetting(setting: SystemSetting) {
  saving[setting.settingKey] = true
  try {
    const val = String(editValues[setting.settingKey])
    const { data } = await settingsApi.update(setting.settingKey, val)
    // Update original value on success
    originalValues[setting.settingKey] = toEditValue(data)
    editValues[setting.settingKey] = toEditValue(data)
    // Update the stored setting object
    const catSettings = settingsByCategory.value[setting.category]
    if (catSettings) {
      const idx = catSettings.findIndex(s => s.settingKey === setting.settingKey)
      if (idx >= 0) catSettings[idx] = data
    }
    showSnack('Setting saved successfully', 'success')
  } catch (e: any) {
    showSnack(e.response?.data?.error || 'Failed to save setting', 'error')
  } finally {
    saving[setting.settingKey] = false
  }
}

async function handleClearCache() {
  clearingCache.value = true
  try {
    await settingsApi.clearCache()
    showSnack('Cache cleared', 'success')
  } catch {
    showSnack('Failed to clear cache', 'error')
  } finally {
    clearingCache.value = false
  }
}

function showSnack(text: string, color: string) {
  snackbar.value = { show: true, text, color }
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleString()
}

// ── Navigation guard ─────────────────────────────────────

onBeforeRouteLeave((_to, _from, next) => {
  if (hasUnsavedChanges.value) {
    showUnsavedDialog.value = true
    pendingNavigation = () => next()
    next(false)
  } else {
    next()
  }
})

function confirmNavigation() {
  showUnsavedDialog.value = false
  if (pendingNavigation) pendingNavigation()
}

function cancelNavigation() {
  showUnsavedDialog.value = false
  pendingNavigation = null
}

// Warn on browser close
function onBeforeUnload(e: BeforeUnloadEvent) {
  if (hasUnsavedChanges.value) {
    e.preventDefault()
  }
}

// ── Lifecycle ────────────────────────────────────────────

async function fetchSettings() {
  loading.value = true
  error.value = null
  try {
    const { data } = await settingsApi.getAll()
    settingsByCategory.value = data
    // Initialize edit values
    for (const cat of Object.keys(data)) {
      for (const setting of data[cat] as SystemSetting[]) {
        const val = toEditValue(setting)
        editValues[setting.settingKey] = val
        originalValues[setting.settingKey] = val
      }
    }
    // Set first tab active
    const cats = Object.keys(data)
    if (cats.length > 0) activeTab.value = cats[0]!
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to load settings'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchSettings()
  window.addEventListener('beforeunload', onBeforeUnload)
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', onBeforeUnload)
})
</script>
