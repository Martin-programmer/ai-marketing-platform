<template>
  <v-container fluid>
    <div class="d-flex align-center mb-2">
      <div>
        <h1 class="text-h4">AI Prompt Templates</h1>
        <p class="text-subtitle-1 text-medium-emphasis">
          Edit and version-control all AI prompts used across the platform.
        </p>
      </div>
      <v-spacer />
      <v-btn
        variant="tonal"
        color="primary"
        prepend-icon="mdi-cached"
        :loading="clearingCache"
        @click="handleClearCache"
      >
        Clear Cache
      </v-btn>
    </div>

    <v-alert v-if="error" type="error" closable class="mb-4" @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- Loading -->
    <template v-if="loading">
      <v-row>
        <v-col cols="3"><v-skeleton-loader type="list-item@6" /></v-col>
        <v-col cols="9"><v-skeleton-loader type="card" /></v-col>
      </v-row>
    </template>

    <!-- Main layout: sidebar + content -->
    <v-row v-else>
      <!-- Module sidebar -->
      <v-col cols="12" md="3">
        <v-card>
          <v-card-title class="text-subtitle-1">Modules</v-card-title>
          <v-list nav density="compact" color="primary" mandatory v-model:selected="selectedModuleArr">
            <v-list-item
              v-for="mod in modules"
              :key="mod"
              :value="mod"
              :title="formatModuleName(mod)"
              :prepend-icon="moduleIcon(mod)"
            >
              <template #append>
                <v-chip size="x-small" variant="tonal">
                  {{ (promptsByModule[mod] || []).length }}
                </v-chip>
              </template>
            </v-list-item>
          </v-list>
        </v-card>
      </v-col>

      <!-- Prompt editor -->
      <v-col cols="12" md="9">
        <template v-if="selectedModule && currentPrompts.length > 0">
          <v-card v-for="prompt in currentPrompts" :key="prompt.id" class="mb-6 pa-4">
            <!-- Header -->
            <div class="d-flex align-center mb-2">
              <div>
                <h3 class="text-h6">{{ prompt.promptName }}</h3>
                <p class="text-caption text-medium-emphasis">{{ prompt.description }}</p>
              </div>
              <v-spacer />
              <v-chip size="small" variant="outlined" class="mr-2">
                v{{ prompt.version }}
              </v-chip>
              <span v-if="prompt.updatedAt" class="text-caption text-medium-emphasis">
                {{ formatDate(prompt.updatedAt) }}
              </span>
            </div>

            <!-- Prompt textarea -->
            <div class="prompt-editor-wrapper mb-3">
              <v-textarea
                v-model="editTexts[promptKey(prompt)]"
                variant="outlined"
                rows="10"
                auto-grow
                max-rows="30"
                hide-details
                style="font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 13px"
                counter
                :maxlength="10000"
              />
            </div>

            <!-- Variable pills -->
            <div v-if="extractVariables(editTexts[promptKey(prompt)] || '').length > 0" class="mb-3">
              <span class="text-caption text-medium-emphasis mr-2">Variables:</span>
              <v-chip
                v-for="v in extractVariables(editTexts[promptKey(prompt)] || '')"
                :key="v"
                size="small"
                color="primary"
                variant="tonal"
                class="mr-1 mb-1"
              >
                {{ '{' + v + '}' }}
              </v-chip>
            </div>

            <!-- Character count -->
            <div class="d-flex align-center mb-3 text-caption text-medium-emphasis">
              <span>{{ (editTexts[promptKey(prompt)] || '').length }} / 10,000 chars</span>
              <span class="ml-4">~{{ estimateTokens(editTexts[promptKey(prompt)] || '') }} tokens</span>
            </div>

            <!-- Action buttons -->
            <div class="d-flex ga-2">
              <v-btn
                color="primary"
                variant="flat"
                prepend-icon="mdi-content-save"
                :loading="saving[promptKey(prompt)]"
                :disabled="!isPromptModified(prompt)"
                @click="savePrompt(prompt)"
              >
                Save
              </v-btn>
              <v-btn
                variant="text"
                :disabled="!isPromptModified(prompt)"
                @click="resetPrompt(prompt)"
              >
                Reset
              </v-btn>
              <v-btn
                variant="tonal"
                prepend-icon="mdi-history"
                @click="openHistory(prompt)"
              >
                History
              </v-btn>
              <v-btn
                variant="tonal"
                prepend-icon="mdi-eye"
                @click="openPreview(prompt)"
              >
                Preview
              </v-btn>
            </div>
          </v-card>
        </template>

        <!-- Empty state -->
        <v-card v-else-if="selectedModule" class="pa-8 text-center">
          <v-icon size="64" color="grey-lighten-1">mdi-robot-confused</v-icon>
          <p class="mt-4 text-medium-emphasis">No prompts found for this module.</p>
        </v-card>
      </v-col>
    </v-row>

    <!-- History dialog -->
    <v-dialog v-model="historyDialog" max-width="900" scrollable>
      <v-card>
        <v-card-title class="d-flex align-center">
          <span>Version History — {{ historyPromptName }}</span>
          <v-spacer />
          <v-btn icon="mdi-close" variant="text" @click="historyDialog = false" />
        </v-card-title>
        <v-card-text>
          <v-alert v-if="historyLoading" type="info" variant="tonal">Loading history...</v-alert>
          <v-table v-else-if="historyItems.length > 0" density="comfortable">
            <thead>
              <tr>
                <th>Version</th>
                <th>Date</th>
                <th>Updated By</th>
                <th>Status</th>
                <th class="text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in historyItems" :key="item.id">
                <td>
                  <v-chip size="small" :color="item.isActive ? 'success' : 'grey'" variant="flat">
                    v{{ item.version }}
                  </v-chip>
                </td>
                <td>{{ formatDate(item.updatedAt || item.createdAt) }}</td>
                <td>{{ item.updatedBy || '—' }}</td>
                <td>
                  <v-chip size="x-small" :color="item.isActive ? 'success' : 'grey'" variant="tonal">
                    {{ item.isActive ? 'Active' : 'Inactive' }}
                  </v-chip>
                </td>
                <td class="text-right">
                  <v-btn
                    size="small"
                    variant="text"
                    prepend-icon="mdi-eye"
                    @click="viewHistoryVersion(item)"
                  >
                    View
                  </v-btn>
                  <v-btn
                    v-if="!item.isActive"
                    size="small"
                    variant="tonal"
                    color="warning"
                    prepend-icon="mdi-undo"
                    :loading="reverting"
                    @click="revertToVersion(item)"
                  >
                    Revert
                  </v-btn>
                </td>
              </tr>
            </tbody>
          </v-table>
          <p v-else class="text-medium-emphasis text-center py-4">No version history available.</p>

          <!-- Version diff viewer -->
          <v-expand-transition>
            <v-card v-if="viewingVersion" variant="outlined" class="mt-4 pa-4">
              <div class="d-flex align-center mb-2">
                <span class="text-subtitle-2">Version {{ viewingVersion.version }} prompt text</span>
                <v-spacer />
                <v-btn icon="mdi-close" size="small" variant="text" @click="viewingVersion = null" />
              </div>
              <pre class="prompt-preview-text">{{ viewingVersion.promptText }}</pre>
            </v-card>
          </v-expand-transition>
        </v-card-text>
      </v-card>
    </v-dialog>

    <!-- Preview dialog -->
    <v-dialog v-model="previewDialog" max-width="800">
      <v-card>
        <v-card-title class="d-flex align-center">
          <span>Prompt Preview</span>
          <v-spacer />
          <v-btn icon="mdi-close" variant="text" @click="previewDialog = false" />
        </v-card-title>
        <v-card-text>
          <p class="text-caption text-medium-emphasis mb-3">
            Fill in sample values for variables to see the rendered prompt.
          </p>

          <!-- Variable inputs -->
          <v-text-field
            v-for="v in previewVariables"
            :key="v"
            v-model="previewValues[v]"
            :label="'{' + v + '}'"
            variant="outlined"
            density="compact"
            class="mb-2"
            style="max-width: 500px"
          />

          <!-- Rendered output -->
          <v-card variant="outlined" class="mt-3 pa-4 bg-grey-lighten-4">
            <div class="text-subtitle-2 mb-2">Rendered Prompt</div>
            <pre class="prompt-preview-text">{{ renderedPreview }}</pre>
          </v-card>
        </v-card-text>
      </v-card>
    </v-dialog>

    <!-- Snackbar -->
    <v-snackbar v-model="snackbar.show" :color="snackbar.color" :timeout="3000" location="bottom end">
      {{ snackbar.text }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive, watch } from 'vue'
import { promptsApi } from '@/api/adminConfig'

// ── Types ────────────────────────────────────────────────

interface PromptTemplate {
  id: string
  module: string
  promptName: string
  promptText: string
  description: string
  version: number
  isActive: boolean
  createdAt: string | null
  updatedAt: string | null
  updatedBy: string | null
}

// ── State ────────────────────────────────────────────────

const loading = ref(true)
const error = ref<string | null>(null)
const clearingCache = ref(false)

const promptsByModule = ref<Record<string, PromptTemplate[]>>({})
const editTexts = reactive<Record<string, string>>({})
const originalTexts = reactive<Record<string, string>>({})
const saving = reactive<Record<string, boolean>>({})

const selectedModuleArr = ref<string[]>([])
const selectedModule = computed(() => selectedModuleArr.value[0] || null)

const modules = computed(() => Object.keys(promptsByModule.value))
const currentPrompts = computed(() =>
  selectedModule.value ? (promptsByModule.value[selectedModule.value] || []) : []
)

// History
const historyDialog = ref(false)
const historyLoading = ref(false)
const historyItems = ref<PromptTemplate[]>([])
const historyPromptName = ref('')
const historyModule = ref('')
const viewingVersion = ref<PromptTemplate | null>(null)
const reverting = ref(false)

// Preview
const previewDialog = ref(false)
const previewSource = ref('')
const previewVariables = ref<string[]>([])
const previewValues = reactive<Record<string, string>>({})

const snackbar = ref({ show: false, text: '', color: 'success' })

// ── Helpers ──────────────────────────────────────────────

function promptKey(p: PromptTemplate) {
  return `${p.module}::${p.promptName}`
}

function formatModuleName(mod: string) {
  return mod.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function moduleIcon(mod: string) {
  const icons: Record<string, string> = {
    CREATIVE_ANALYZER: 'mdi-image-search',
    COPY_FACTORY: 'mdi-text-box-edit',
    CAMPAIGN_CREATOR: 'mdi-bullhorn',
    OPTIMIZER_ENRICHMENT: 'mdi-chart-line',
    AI_REPORTER: 'mdi-file-chart',
    CLIENT_BRIEFER: 'mdi-briefcase',
    AUDIENCE_ARCHITECT: 'mdi-account-group',
    BUDGET_STRATEGIST: 'mdi-cash-multiple',
    CLIENT_PORTAL_AI: 'mdi-chat-question',
    WEEKLY_DIGEST: 'mdi-email-newsletter',
    AGENCY_INTELLIGENCE: 'mdi-brain',
  }
  return icons[mod] || 'mdi-robot'
}

function extractVariables(text: string): string[] {
  const matches = text.match(/\{(\w+)\}/g) || []
  const unique = [...new Set(matches.map(m => m.slice(1, -1)))]
  // Filter out common Java format specifiers
  return unique.filter(v => !['s', 'd', 'f', 'n', 't'].includes(v) && v.length > 1)
}

function estimateTokens(text: string): number {
  // Rough estimate: ~4 chars per token
  return Math.round(text.length / 4)
}

function isPromptModified(prompt: PromptTemplate): boolean {
  const key = promptKey(prompt)
  return editTexts[key] !== originalTexts[key]
}

function resetPrompt(prompt: PromptTemplate) {
  const key = promptKey(prompt)
  editTexts[key] = originalTexts[key]!
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleString()
}

function showSnack(text: string, color: string) {
  snackbar.value = { show: true, text, color }
}

// ── Computed preview ─────────────────────────────────────

const renderedPreview = computed(() => {
  let text = previewSource.value
  for (const [key, val] of Object.entries(previewValues)) {
    text = text.replace(new RegExp(`\\{${key}\\}`, 'g'), val || `{${key}}`)
  }
  return text
})

// ── API Methods ──────────────────────────────────────────

async function fetchPrompts() {
  loading.value = true
  error.value = null
  try {
    const { data } = await promptsApi.getAll()
    promptsByModule.value = data
    for (const mod of Object.keys(data)) {
      for (const p of data[mod] as PromptTemplate[]) {
        const key = promptKey(p)
        editTexts[key] = p.promptText
        originalTexts[key] = p.promptText
      }
    }
    // Select first module
    const mods = Object.keys(data)
    if (mods.length > 0) selectedModuleArr.value = [mods[0]!]
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to load prompts'
  } finally {
    loading.value = false
  }
}

async function savePrompt(prompt: PromptTemplate) {
  const key = promptKey(prompt)
  saving[key] = true
  try {
    const { data } = await promptsApi.update(prompt.module, prompt.promptName, editTexts[key]!)
    // Update the stored prompt
    const modPrompts = promptsByModule.value[prompt.module]
    if (modPrompts) {
      const idx = modPrompts.findIndex(p => p.promptName === prompt.promptName)
      if (idx >= 0) modPrompts[idx] = data
    }
    originalTexts[key] = data.promptText
    editTexts[key] = data.promptText
    showSnack('Prompt saved (new version created)', 'success')
  } catch (e: any) {
    showSnack(e.response?.data?.error || 'Failed to save prompt', 'error')
  } finally {
    saving[key] = false
  }
}

async function handleClearCache() {
  clearingCache.value = true
  try {
    await promptsApi.clearCache()
    showSnack('Prompt cache cleared', 'success')
  } catch {
    showSnack('Failed to clear cache', 'error')
  } finally {
    clearingCache.value = false
  }
}

// ── History ──────────────────────────────────────────────

async function openHistory(prompt: PromptTemplate) {
  historyPromptName.value = prompt.promptName
  historyModule.value = prompt.module
  historyDialog.value = true
  historyLoading.value = true
  viewingVersion.value = null
  try {
    const { data } = await promptsApi.getHistory(prompt.module, prompt.promptName)
    historyItems.value = data
  } catch (e: any) {
    showSnack('Failed to load history', 'error')
    historyItems.value = []
  } finally {
    historyLoading.value = false
  }
}

function viewHistoryVersion(item: PromptTemplate) {
  viewingVersion.value = item
}

async function revertToVersion(item: PromptTemplate) {
  reverting.value = true
  try {
    const { data } = await promptsApi.revert(historyModule.value, item.promptName, item.version)
    // Refresh the main prompt list
    const key = `${historyModule.value}::${item.promptName}`
    originalTexts[key] = data.promptText
    editTexts[key] = data.promptText
    // Update in module list
    const modPrompts = promptsByModule.value[historyModule.value]
    if (modPrompts) {
      const idx = modPrompts.findIndex(p => p.promptName === item.promptName)
      if (idx >= 0) modPrompts[idx] = data
    }
    showSnack(`Reverted to version ${item.version}`, 'success')
    historyDialog.value = false
  } catch (e: any) {
    showSnack(e.response?.data?.error || 'Failed to revert', 'error')
  } finally {
    reverting.value = false
  }
}

// ── Preview ──────────────────────────────────────────────

function openPreview(prompt: PromptTemplate) {
  const key = promptKey(prompt)
  previewSource.value = editTexts[key] || prompt.promptText
  previewVariables.value = extractVariables(previewSource.value)
  // Clear old values
  for (const k of Object.keys(previewValues)) delete previewValues[k]
  for (const v of previewVariables.value) previewValues[v] = ''
  previewDialog.value = true
}

// ── Watch module changes ─────────────────────────────────

watch(selectedModule, () => {
  // Nothing extra needed, computed handles it
})

// ── Lifecycle ────────────────────────────────────────────

onMounted(fetchPrompts)
</script>

<style scoped>
.prompt-preview-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.5;
  max-height: 500px;
  overflow-y: auto;
}
</style>
