<template>
  <v-container fluid>
    <div class="d-flex align-center mb-2">
      <div>
        <h1 class="text-h4">Email Templates</h1>
        <p class="text-subtitle-1 text-medium-emphasis">
          Edit email templates and preview them with sample data.
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

    <!-- Main layout: sidebar + editor -->
    <v-row v-else>
      <!-- Template list sidebar -->
      <v-col cols="12" md="3">
        <v-card>
          <v-card-title class="text-subtitle-1">Templates</v-card-title>
          <v-list nav density="compact" color="primary" mandatory v-model:selected="selectedKeyArr">
            <v-list-item
              v-for="tpl in templates"
              :key="tpl.templateKey"
              :value="tpl.templateKey"
              :title="formatTemplateName(tpl.templateKey)"
              :subtitle="tpl.description"
              :prepend-icon="templateIcon(tpl.templateKey)"
            />
          </v-list>
        </v-card>
      </v-col>

      <!-- Editor -->
      <v-col cols="12" md="9">
        <template v-if="currentTemplate">
          <v-card class="pa-4">
            <!-- Header -->
            <div class="d-flex align-center mb-4">
              <div>
                <h3 class="text-h6">{{ formatTemplateName(currentTemplate.templateKey) }}</h3>
                <p class="text-caption text-medium-emphasis">{{ currentTemplate.description }}</p>
              </div>
              <v-spacer />
              <span v-if="currentTemplate.updatedAt" class="text-caption text-medium-emphasis">
                Updated {{ formatDate(currentTemplate.updatedAt) }}
              </span>
            </div>

            <!-- Available variables -->
            <div v-if="currentTemplate.availableVars" class="mb-4">
              <span class="text-caption text-medium-emphasis mr-2">Available variables:</span>
              <v-chip
                v-for="v in parseVars(currentTemplate.availableVars)"
                :key="v"
                size="small"
                color="primary"
                variant="tonal"
                class="mr-1 mb-1"
                @click="insertVariable(v)"
              >
                {{ wrapVar(v) }}
                <v-tooltip activator="parent" location="top">Click to copy</v-tooltip>
              </v-chip>
            </div>

            <!-- Subject line -->
            <v-text-field
              v-if="currentTemplate.templateKey !== 'OUTER_WRAPPER'"
              v-model="editSubject"
              label="Subject Line"
              variant="outlined"
              density="compact"
              class="mb-4"
              :hint="'Use \x7B\x7Bvariable\x7D\x7D syntax for dynamic content'"
              persistent-hint
            />

            <!-- Split view: editor + preview -->
            <div class="d-flex align-center mb-2">
              <span class="text-subtitle-2">HTML Body</span>
              <v-spacer />
              <v-btn-toggle v-model="previewMode" density="compact" variant="outlined" mandatory>
                <v-btn value="edit" size="small">
                  <v-icon start>mdi-pencil</v-icon> Edit
                </v-btn>
                <v-btn value="split" size="small">
                  <v-icon start>mdi-arrow-split-vertical</v-icon> Split
                </v-btn>
                <v-btn value="preview" size="small">
                  <v-icon start>mdi-eye</v-icon> Preview
                </v-btn>
              </v-btn-toggle>
              <v-btn
                class="ml-2"
                size="small"
                variant="tonal"
                :icon="mobilePreview ? 'mdi-cellphone' : 'mdi-monitor'"
                @click="mobilePreview = !mobilePreview"
              >
                <v-tooltip activator="parent" location="top">
                  {{ mobilePreview ? 'Mobile preview' : 'Desktop preview' }}
                </v-tooltip>
              </v-btn>
            </div>

            <v-row>
              <!-- Editor -->
              <v-col
                v-if="previewMode !== 'preview'"
                :cols="previewMode === 'split' ? 6 : 12"
              >
                <v-textarea
                  v-model="editHtml"
                  variant="outlined"
                  rows="18"
                  auto-grow
                  max-rows="40"
                  hide-details
                  style="font-family: 'JetBrains Mono', 'Fira Code', monospace; font-size: 12px"
                />
              </v-col>

              <!-- Preview -->
              <v-col
                v-if="previewMode !== 'edit'"
                :cols="previewMode === 'split' ? 6 : 12"
              >
                <v-card
                  variant="outlined"
                  class="preview-container"
                  :class="{ 'mobile-preview': mobilePreview }"
                >
                  <iframe
                    ref="previewFrame"
                    :srcdoc="renderedPreview"
                    sandbox="allow-same-origin"
                    class="preview-iframe"
                    :style="{ width: mobilePreview ? '375px' : '100%' }"
                  />
                </v-card>
              </v-col>
            </v-row>

            <!-- HTML validation warnings -->
            <v-alert
              v-if="htmlWarning"
              type="warning"
              variant="tonal"
              density="compact"
              class="mt-3"
              icon="mdi-code-tags"
            >
              {{ htmlWarning }}
            </v-alert>

            <!-- Action buttons -->
            <div class="d-flex ga-2 mt-4">
              <v-btn
                color="primary"
                variant="flat"
                prepend-icon="mdi-content-save"
                :loading="saving"
                :disabled="!isModified"
                @click="saveTemplate"
              >
                Save
              </v-btn>
              <v-btn
                variant="text"
                :disabled="!isModified"
                @click="resetTemplate"
              >
                Reset
              </v-btn>
              <v-btn
                variant="tonal"
                prepend-icon="mdi-email-fast"
                :loading="previewing"
                @click="requestServerPreview"
              >
                Server Preview
              </v-btn>
            </div>
          </v-card>
        </template>
      </v-col>
    </v-row>

    <!-- Server preview dialog (full rendered HTML from server) -->
    <v-dialog v-model="serverPreviewDialog" max-width="800" scrollable>
      <v-card>
        <v-card-title class="d-flex align-center">
          <span>Server-Rendered Preview</span>
          <v-spacer />
          <v-btn icon="mdi-close" variant="text" @click="serverPreviewDialog = false" />
        </v-card-title>
        <v-card-text>
          <p class="text-caption text-medium-emphasis mb-3">
            This is how the email will look when rendered by the server with sample data.
          </p>
          <div v-if="serverPreviewSubject" class="mb-3">
            <span class="text-subtitle-2">Subject: </span>
            <span>{{ serverPreviewSubject }}</span>
          </div>
          <v-card variant="outlined">
            <iframe
              :srcdoc="serverPreviewHtml"
              sandbox="allow-same-origin"
              style="width: 100%; height: 600px; border: none"
            />
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
import { ref, computed, onMounted, watch } from 'vue'
import { emailTemplatesApi } from '@/api/adminConfig'

// ── Types ────────────────────────────────────────────────

interface EmailTemplate {
  id: string
  templateKey: string
  subject: string
  htmlBody: string
  description: string
  availableVars: string
  isActive: boolean
  updatedAt: string | null
  updatedBy: string | null
}

// ── State ────────────────────────────────────────────────

const loading = ref(true)
const error = ref<string | null>(null)
const clearingCache = ref(false)
const saving = ref(false)
const previewing = ref(false)

const templates = ref<EmailTemplate[]>([])
const selectedKeyArr = ref<string[]>([])
const selectedKey = computed(() => selectedKeyArr.value[0] || null)
const currentTemplate = computed(() =>
  templates.value.find(t => t.templateKey === selectedKey.value) || null
)

const editSubject = ref('')
const editHtml = ref('')
const originalSubject = ref('')
const originalHtml = ref('')

const previewMode = ref<'edit' | 'split' | 'preview'>('split')
const mobilePreview = ref(false)
const previewFrame = ref<HTMLIFrameElement | null>(null)

// Server preview
const serverPreviewDialog = ref(false)
const serverPreviewHtml = ref('')
const serverPreviewSubject = ref('')

const snackbar = ref({ show: false, text: '', color: 'success' })

// ── Helpers ──────────────────────────────────────────────

function formatTemplateName(key: string) {
  return key.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())
}

function wrapVar(v: string) {
  return '\u007B\u007B' + v + '\u007D\u007D'
}

function templateIcon(key: string) {
  const icons: Record<string, string> = {
    INVITATION: 'mdi-email-plus',
    PASSWORD_RESET: 'mdi-lock-reset',
    WELCOME: 'mdi-hand-wave',
    REPORT_SENT: 'mdi-chart-box',
    ALERT: 'mdi-alert-circle',
    CAMPAIGN_PUBLISHED: 'mdi-rocket-launch',
    TWO_FACTOR_CODE: 'mdi-shield-key',
    OUTER_WRAPPER: 'mdi-page-layout-header-footer',
    WEEKLY_DIGEST: 'mdi-email-newsletter',
  }
  return icons[key] || 'mdi-email-edit'
}

function parseVars(vars: string): string[] {
  if (!vars) return []
  return vars.split(',').map(v => v.trim()).filter(Boolean)
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '—'
  return new Date(dateStr).toLocaleString()
}

function showSnack(text: string, color: string) {
  snackbar.value = { show: true, text, color }
}

function insertVariable(v: string) {
  navigator.clipboard.writeText(`{{${v}}}`)
  showSnack(`Copied {{${v}}} to clipboard`, 'info')
}

// ── Computed ─────────────────────────────────────────────

const isModified = computed(() => {
  return editSubject.value !== originalSubject.value || editHtml.value !== originalHtml.value
})

const renderedPreview = computed(() => {
  let html = editHtml.value
  // Replace variables with sample highlighted values
  html = html.replace(/\{\{(\w+)\}\}/g, '<span style="background-color:#BBDEFB;padding:1px 4px;border-radius:3px;color:#0D47A1;font-weight:bold">[$1]</span>')
  return html
})

const htmlWarning = computed(() => {
  const html = editHtml.value
  if (!html) return null
  // Basic tag balance check
  const openTags = (html.match(/<[a-zA-Z][^>]*[^/]>/g) || []).length
  const closeTags = (html.match(/<\/[a-zA-Z][^>]*>/g) || []).length
  const selfClosing = (html.match(/<[^>]+\/>/g) || []).length
  if (Math.abs(openTags - selfClosing - closeTags) > 3) {
    return `Tag balance warning: ${openTags} opening tags, ${closeTags} closing tags, ${selfClosing} self-closing. Some tags may be unclosed.`
  }
  return null
})

// ── Watch template selection ─────────────────────────────

watch(selectedKey, () => {
  if (currentTemplate.value) {
    editSubject.value = currentTemplate.value.subject || ''
    editHtml.value = currentTemplate.value.htmlBody || ''
    originalSubject.value = currentTemplate.value.subject || ''
    originalHtml.value = currentTemplate.value.htmlBody || ''
  }
})

// ── API Methods ──────────────────────────────────────────

async function fetchTemplates() {
  loading.value = true
  error.value = null
  try {
    const { data } = await emailTemplatesApi.getAll()
    templates.value = data
    if (data.length > 0) {
      selectedKeyArr.value = [data[0].templateKey]
    }
  } catch (e: any) {
    error.value = e.response?.data?.error || 'Failed to load email templates'
  } finally {
    loading.value = false
  }
}

async function saveTemplate() {
  if (!currentTemplate.value) return
  saving.value = true
  try {
    const { data } = await emailTemplatesApi.update(
      currentTemplate.value.templateKey,
      editSubject.value,
      editHtml.value
    )
    // Update in list
    const idx = templates.value.findIndex(t => t.templateKey === data.templateKey)
    if (idx >= 0) templates.value[idx] = data
    originalSubject.value = data.subject
    originalHtml.value = data.htmlBody
    showSnack('Email template saved', 'success')
  } catch (e: any) {
    showSnack(e.response?.data?.error || 'Failed to save template', 'error')
  } finally {
    saving.value = false
  }
}

function resetTemplate() {
  editSubject.value = originalSubject.value
  editHtml.value = originalHtml.value
}

async function requestServerPreview() {
  if (!currentTemplate.value) return
  previewing.value = true
  try {
    const { data } = await emailTemplatesApi.preview(currentTemplate.value.templateKey)
    serverPreviewSubject.value = data.subject || ''
    serverPreviewHtml.value = data.html || data.body || ''
    serverPreviewDialog.value = true
  } catch (e: any) {
    showSnack(e.response?.data?.error || 'Failed to generate preview', 'error')
  } finally {
    previewing.value = false
  }
}

async function handleClearCache() {
  clearingCache.value = true
  try {
    await emailTemplatesApi.clearCache()
    showSnack('Email template cache cleared', 'success')
  } catch {
    showSnack('Failed to clear cache', 'error')
  } finally {
    clearingCache.value = false
  }
}

// ── Lifecycle ────────────────────────────────────────────

onMounted(fetchTemplates)
</script>

<style scoped>
.preview-container {
  overflow: auto;
  background: #fff;
}

.preview-iframe {
  border: none;
  height: 500px;
  display: block;
  margin: 0 auto;
}

.mobile-preview {
  max-width: 420px;
  margin: 0 auto;
  border: 2px solid #1565C0;
  border-radius: 16px;
  padding: 8px;
}

.mobile-preview .preview-iframe {
  border-radius: 8px;
}
</style>
