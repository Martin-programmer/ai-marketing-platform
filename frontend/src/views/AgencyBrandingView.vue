<template>
  <div>
    <div class="d-flex align-center mb-6">
      <div>
        <h1 class="text-h4 font-weight-bold">Branding</h1>
        <p class="text-body-1 text-medium-emphasis mt-1">
          Customize your agency's appearance across the platform
        </p>
      </div>
    </div>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <v-alert v-if="saved" type="success" variant="tonal" class="mb-4" closable @click:close="saved = false">
      Branding settings saved successfully!
    </v-alert>

    <v-row>
      <!-- Left column: settings -->
      <v-col cols="12" md="8">
        <v-card class="mb-4" variant="outlined">
          <v-card-title class="text-subtitle-1">
            <v-icon start>mdi-link-variant</v-icon>
            Custom Login URL
          </v-card-title>
          <v-card-text>
            <v-text-field
              v-model="form.slug"
              label="Custom URL Slug"
              variant="outlined"
              density="compact"
              :prefix="loginUrlPrefix"
              :append-inner-icon="slugStatus === 'available' ? 'mdi-check-circle' : slugStatus === 'unavailable' ? 'mdi-close-circle' : slugStatus === 'checking' ? 'mdi-loading' : undefined"
              :color="slugStatus === 'available' ? 'success' : slugStatus === 'unavailable' ? 'error' : undefined"
              hide-details="auto"
              @update:model-value="handleSlugInput"
            />
            <p class="text-caption text-medium-emphasis mt-2 mb-1">
              Lowercase letters, numbers and hyphens only. 3-30 characters.
            </p>
            <p class="text-body-2 mt-1" :class="slugMessageClass" v-if="slugMessage">
              {{ slugMessage }}
            </p>
            <p class="text-caption text-medium-emphasis mt-2">
              Share this URL with your clients for a branded login experience.
            </p>

            <div v-if="saved && form.slug" class="d-flex align-center flex-wrap ga-2 mt-3">
              <span class="text-body-2">Your client login URL:</span>
              <a :href="fullLoginUrl" target="_blank" rel="noopener noreferrer">{{ fullLoginUrl }}</a>
              <v-btn size="small" variant="text" prepend-icon="mdi-content-copy" @click="copyLoginUrl">
                Copy
              </v-btn>
            </div>
          </v-card-text>
        </v-card>

        <!-- Logo Upload -->
        <v-card class="mb-4" variant="outlined">
          <v-card-title class="text-subtitle-1">
            <v-icon start>mdi-image</v-icon>
            Logo
          </v-card-title>
          <v-card-text>
            <div class="d-flex align-center ga-4">
              <v-avatar size="80" rounded="lg" color="grey-lighten-3">
                <v-img v-if="form.logoUrl" :src="form.logoUrl" />
                <v-icon v-else size="40" color="grey">mdi-domain</v-icon>
              </v-avatar>
              <div>
                <v-file-input
                  v-model="logoFile"
                  label="Upload Logo"
                  variant="outlined"
                  density="compact"
                  accept="image/png,image/jpeg,image/svg+xml"
                  prepend-icon=""
                  prepend-inner-icon="mdi-upload"
                  :loading="uploading"
                  :disabled="uploading"
                  hide-details
                  style="max-width: 300px"
                  @update:model-value="handleLogoUpload"
                />
                <p class="text-caption text-medium-emphasis mt-1">
                  PNG, JPG, or SVG. Max 2 MB. Recommended: 400×100px
                </p>
              </div>
            </div>
          </v-card-text>
        </v-card>

        <!-- Brand Colors -->
        <v-card class="mb-4" variant="outlined">
          <v-card-title class="text-subtitle-1">
            <v-icon start>mdi-palette</v-icon>
            Brand Colors
          </v-card-title>
          <v-card-text>
            <v-row>
              <v-col cols="12" sm="4">
                <v-text-field
                  v-model="form.primaryColor"
                  label="Primary Color"
                  variant="outlined"
                  density="compact"
                  placeholder="#1565C0"
                  :rules="[colorRule]"
                  hide-details="auto"
                >
                  <template #prepend-inner>
                    <div
                      class="color-swatch"
                      :style="{ background: form.primaryColor || '#1565C0' }"
                    />
                  </template>
                </v-text-field>
              </v-col>
              <v-col cols="12" sm="4">
                <v-text-field
                  v-model="form.secondaryColor"
                  label="Secondary Color"
                  variant="outlined"
                  density="compact"
                  placeholder="#424242"
                  :rules="[colorRule]"
                  hide-details="auto"
                >
                  <template #prepend-inner>
                    <div
                      class="color-swatch"
                      :style="{ background: form.secondaryColor || '#424242' }"
                    />
                  </template>
                </v-text-field>
              </v-col>
              <v-col cols="12" sm="4">
                <v-text-field
                  v-model="form.accentColor"
                  label="Accent Color"
                  variant="outlined"
                  density="compact"
                  placeholder="#FF6F00"
                  :rules="[colorRule]"
                  hide-details="auto"
                >
                  <template #prepend-inner>
                    <div
                      class="color-swatch"
                      :style="{ background: form.accentColor || '#FF6F00' }"
                    />
                  </template>
                </v-text-field>
              </v-col>
            </v-row>
            <v-text-field
              v-model="form.fontFamily"
              label="Font Family"
              variant="outlined"
              density="compact"
              placeholder="e.g., Inter, Roboto"
              class="mt-3"
              hide-details
            />
          </v-card-text>
        </v-card>

        <!-- Company Information -->
        <v-card class="mb-4" variant="outlined">
          <v-card-title class="text-subtitle-1">
            <v-icon start>mdi-domain</v-icon>
            Company Information
          </v-card-title>
          <v-card-text>
            <v-row>
              <v-col cols="12" sm="6">
                <v-text-field
                  v-model="form.companyEmail"
                  label="Company Email"
                  variant="outlined"
                  density="compact"
                  prepend-inner-icon="mdi-email-outline"
                  hide-details
                />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field
                  v-model="form.companyPhone"
                  label="Phone"
                  variant="outlined"
                  density="compact"
                  prepend-inner-icon="mdi-phone-outline"
                  hide-details
                />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field
                  v-model="form.companyWebsite"
                  label="Website"
                  variant="outlined"
                  density="compact"
                  prepend-inner-icon="mdi-web"
                  hide-details
                />
              </v-col>
              <v-col cols="12" sm="6">
                <v-text-field
                  v-model="form.companyAddress"
                  label="Address"
                  variant="outlined"
                  density="compact"
                  prepend-inner-icon="mdi-map-marker-outline"
                  hide-details
                />
              </v-col>
            </v-row>
          </v-card-text>
        </v-card>

        <!-- Portal & Communications -->
        <v-card class="mb-4" variant="outlined">
          <v-card-title class="text-subtitle-1">
            <v-icon start>mdi-message-text</v-icon>
            Portal &amp; Communications
          </v-card-title>
          <v-card-text>
            <v-textarea
              v-model="form.portalWelcomeMessage"
              label="Portal Welcome Message"
              variant="outlined"
              density="compact"
              rows="3"
              placeholder="Welcome! Here you can view your campaign reports and performance data."
              hide-details
              class="mb-3"
            />
            <v-textarea
              v-model="form.emailFooterText"
              label="Email Footer Text"
              variant="outlined"
              density="compact"
              rows="2"
              placeholder="Custom footer text for emails sent to your clients"
              hide-details
              class="mb-3"
            />
            <v-textarea
              v-model="form.reportFooterText"
              label="Report Footer Text"
              variant="outlined"
              density="compact"
              rows="2"
              placeholder="Custom footer text for PDF/HTML reports"
              hide-details
              class="mb-3"
            />
            <v-textarea
              v-model="form.reportDisclaimer"
              label="Report Disclaimer"
              variant="outlined"
              density="compact"
              rows="2"
              placeholder="Disclaimer text shown at the bottom of reports"
              hide-details
            />
          </v-card-text>
        </v-card>

        <!-- Custom CSS -->
        <v-card class="mb-4" variant="outlined">
          <v-card-title class="text-subtitle-1">
            <v-icon start>mdi-code-braces</v-icon>
            Custom CSS (Advanced)
          </v-card-title>
          <v-card-text>
            <v-textarea
              v-model="form.customCss"
              label="Custom CSS"
              variant="outlined"
              density="compact"
              rows="4"
              placeholder="/* Custom CSS rules for client portal */"
              hide-details
              style="font-family: monospace; font-size: 13px"
            />
          </v-card-text>
        </v-card>

        <!-- Save Button -->
        <div class="d-flex justify-end mb-6">
          <v-btn
            color="primary"
            size="large"
            :loading="saving"
            :disabled="saving"
            prepend-icon="mdi-content-save"
            @click="handleSave"
          >
            Save Branding
          </v-btn>
        </div>
      </v-col>

      <!-- Right column: preview -->
      <v-col cols="12" md="4">
        <v-card variant="outlined" class="sticky-preview">
          <v-card-title class="text-subtitle-1">
            <v-icon start>mdi-eye</v-icon>
            Preview
          </v-card-title>
          <v-card-text>
            <!-- Login preview -->
            <div
              class="preview-login rounded-lg pa-4 mb-4"
              :style="{ background: previewGradient }"
            >
              <div class="preview-card rounded pa-3 text-center">
                <v-avatar v-if="form.logoUrl" size="40" rounded="0" class="mb-2">
                  <v-img :src="form.logoUrl" />
                </v-avatar>
                <v-icon v-else size="28" :color="form.primaryColor || '#1565C0'" class="mb-2">mdi-rocket-launch</v-icon>
                <div class="text-body-2 font-weight-bold">Sign In</div>
                <div class="preview-input mt-2" />
                <div class="preview-input mt-1" />
                <div
                  class="preview-btn mt-2 rounded"
                  :style="{ background: form.primaryColor || '#1565C0' }"
                />
              </div>
            </div>

            <!-- Sidebar preview -->
            <div class="text-caption text-medium-emphasis mb-1">Sidebar</div>
            <div class="preview-sidebar rounded-lg pa-3 mb-4">
              <div v-if="form.logoUrl" class="text-center mb-2">
                <v-avatar size="32" rounded="0">
                  <v-img :src="form.logoUrl" />
                </v-avatar>
              </div>
              <div class="preview-nav-item" v-for="i in 4" :key="i" />
              <div
                class="preview-nav-active rounded"
                :style="{ background: (form.primaryColor || '#1565C0') + '20' }"
              />
            </div>

            <!-- Color chips -->
            <div class="text-caption text-medium-emphasis mb-1">Colors</div>
            <div class="d-flex ga-2 flex-wrap">
              <v-chip
                v-if="form.primaryColor"
                size="small"
                :color="form.primaryColor"
                variant="flat"
                class="text-white"
              >
                Primary
              </v-chip>
              <v-chip
                v-if="form.secondaryColor"
                size="small"
                :color="form.secondaryColor"
                variant="flat"
                class="text-white"
              >
                Secondary
              </v-chip>
              <v-chip
                v-if="form.accentColor"
                size="small"
                :color="form.accentColor"
                variant="flat"
                class="text-white"
              >
                Accent
              </v-chip>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import brandingApi, { type AgencyBrandingResponse, type UpdateBrandingRequest } from '@/api/branding'

const loading = ref(true)
const saving = ref(false)
const uploading = ref(false)
const error = ref<string | null>(null)
const saved = ref(false)
const logoFile = ref<File[] | null>(null)
const slugStatus = ref<'idle' | 'checking' | 'available' | 'unavailable'>('idle')
const slugSuggestion = ref<string | null>(null)
let slugCheckTimer: number | null = null
const loginUrlPrefix = 'adverion.xyz/login/'

const form = ref<AgencyBrandingResponse>({
  slug: null,
  logoUrl: null,
  logoS3Key: null,
  primaryColor: null,
  secondaryColor: null,
  accentColor: null,
  fontFamily: null,
  companyEmail: null,
  companyPhone: null,
  companyWebsite: null,
  companyAddress: null,
  emailFooterText: null,
  reportFooterText: null,
  reportDisclaimer: null,
  portalWelcomeMessage: null,
  customCss: null,
})

const slugMessage = computed(() => {
  if (!form.value.slug) return ''
  if (!isSlugFormatValid(form.value.slug)) return 'Slug format is invalid.'
  if (slugStatus.value === 'checking') return 'Checking availability...'
  if (slugStatus.value === 'available') return 'Slug is available.'
  if (slugStatus.value === 'unavailable') {
    return slugSuggestion.value ? `Slug is unavailable. Try "${slugSuggestion.value}".` : 'Slug is unavailable.'
  }
  return ''
})

const slugMessageClass = computed(() => {
  if (slugStatus.value === 'available') return 'text-success'
  if (slugStatus.value === 'unavailable') return 'text-error'
  return 'text-medium-emphasis'
})

const fullLoginUrl = computed(() => `https://${loginUrlPrefix}${form.value.slug}`)

const colorRule = (v: string | null) => {
  if (!v) return true
  return /^#[0-9A-Fa-f]{6}$/.test(v) || 'Invalid hex color (e.g. #1565C0)'
}

const previewGradient = computed(() => {
  const base = form.value.primaryColor || '#1565C0'
  return `linear-gradient(135deg, ${base} 0%, ${darken(base, 20)} 100%)`
})

function darken(hex: string, pct: number): string {
  try {
    const n = parseInt(hex.replace('#', ''), 16)
    const f = 1 - pct / 100
    const r = Math.max(0, Math.round(((n >> 16) & 0xff) * f))
    const g = Math.max(0, Math.round(((n >> 8) & 0xff) * f))
    const b = Math.max(0, Math.round((n & 0xff) * f))
    return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`
  } catch {
    return hex
  }
}

onMounted(async () => {
  try {
    const { data } = await brandingApi.getAgencyBranding()
    Object.assign(form.value, data)
    if (form.value.slug) {
      slugStatus.value = 'available'
    }
  } catch (e: unknown) {
    error.value = getErrorMessage(e, 'Failed to load branding settings')
  } finally {
    loading.value = false
  }
})

watch(() => form.value.slug, (value) => {
  saved.value = false
  slugSuggestion.value = null

  if (slugCheckTimer !== null) {
    window.clearTimeout(slugCheckTimer)
    slugCheckTimer = null
  }

  if (!value) {
    slugStatus.value = 'idle'
    return
  }

  if (!isSlugFormatValid(value)) {
    slugStatus.value = 'unavailable'
    return
  }

  slugStatus.value = 'checking'
  slugCheckTimer = window.setTimeout(async () => {
    try {
      const { data } = await brandingApi.checkSlug(value)
      slugStatus.value = data.available ? 'available' : 'unavailable'
      slugSuggestion.value = data.suggestion ?? null
    } catch {
      slugStatus.value = 'idle'
    }
  }, 500)
})

onUnmounted(() => {
  if (slugCheckTimer !== null) {
    window.clearTimeout(slugCheckTimer)
  }
})

function handleSlugInput(value: string | null) {
  if (value == null) {
    form.value.slug = null
    return
  }
  form.value.slug = value
    .toLowerCase()
    .replace(/\s+/g, '-')
    .replace(/[^a-z0-9-]/g, '')
}

function isSlugFormatValid(value: string) {
  return /^[a-z0-9-]{3,30}$/.test(value)
    && !value.startsWith('-')
    && !value.endsWith('-')
    && !value.includes('--')
    && !['login', 'admin', 'owner', 'portal', 'api', 'auth', 'privacy', 'terms', 'public'].includes(value)
}

async function copyLoginUrl() {
  await navigator.clipboard.writeText(fullLoginUrl.value)
}

async function handleLogoUpload(files: File[] | null) {
  const file = files?.[0]
  if (!file) return

  if (file.size > 2 * 1024 * 1024) {
    error.value = 'Logo file must be smaller than 2 MB'
    logoFile.value = null
    return
  }

  uploading.value = true
  error.value = null
  try {
    const { data } = await brandingApi.uploadAgencyLogo(file)
    form.value.logoUrl = data.logoUrl
    saved.value = false
  } catch (e: unknown) {
    error.value = getErrorMessage(e, 'Failed to upload logo')
  } finally {
    uploading.value = false
    logoFile.value = null
  }
}

async function handleSave() {
  if (form.value.slug && (slugStatus.value === 'checking' || slugStatus.value === 'unavailable' || !isSlugFormatValid(form.value.slug))) {
    error.value = slugMessage.value || 'Please choose a valid available slug before saving.'
    return
  }

  saving.value = true
  error.value = null
  saved.value = false
  try {
    const payload: UpdateBrandingRequest = {
      slug: form.value.slug || null,
      primaryColor: form.value.primaryColor || null,
      secondaryColor: form.value.secondaryColor || null,
      accentColor: form.value.accentColor || null,
      fontFamily: form.value.fontFamily || null,
      companyEmail: form.value.companyEmail || null,
      companyPhone: form.value.companyPhone || null,
      companyWebsite: form.value.companyWebsite || null,
      companyAddress: form.value.companyAddress || null,
      emailFooterText: form.value.emailFooterText || null,
      reportFooterText: form.value.reportFooterText || null,
      reportDisclaimer: form.value.reportDisclaimer || null,
      portalWelcomeMessage: form.value.portalWelcomeMessage || null,
      customCss: form.value.customCss || null,
    }
    const { data } = await brandingApi.updateAgencyBranding(payload)
    Object.assign(form.value, data)
    saved.value = true
  } catch (e: unknown) {
    error.value = getErrorMessage(e, 'Failed to save branding settings')
  } finally {
    saving.value = false
  }
}

function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === 'object' && error !== null && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response
    return response?.data?.message || fallback
  }
  return fallback
}
</script>

<style scoped>
.sticky-preview {
  position: sticky;
  top: 80px;
}

.color-swatch {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.12);
}

.preview-login {
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-card {
  background: white;
  width: 100%;
  max-width: 160px;
}

.preview-input {
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
}

.preview-btn {
  height: 10px;
  border-radius: 4px;
}

.preview-sidebar {
  background: #fafafa;
  border: 1px solid #e0e0e0;
}

.preview-nav-item {
  height: 8px;
  background: #e0e0e0;
  border-radius: 4px;
  margin-bottom: 6px;
  width: 80%;
}

.preview-nav-active {
  height: 8px;
  border-radius: 4px;
  margin-bottom: 6px;
  width: 80%;
}
</style>
