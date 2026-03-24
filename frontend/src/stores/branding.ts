import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useTheme } from 'vuetify'
import brandingApi, { type PublicBrandingResponse, type PortalBrandingResponse } from '@/api/branding'
import portalApi from '@/api/portal'

/** Default platform colors (match vuetify.ts defaults) */
const DEFAULTS = {
  platformName: 'AI Marketing Platform',
  primaryColor: '#1565C0',
  secondaryColor: '#424242',
  accentColor: '#FF6F00',
}

export const useBrandingStore = defineStore('branding', () => {
  // ── State ──
  const loaded = ref(false)
  const loading = ref(false)
  const name = ref(DEFAULTS.platformName)
  const logoUrl = ref<string | null>(null)
  const primaryColor = ref(DEFAULTS.primaryColor)
  const secondaryColor = ref(DEFAULTS.secondaryColor)
  const accentColor = ref(DEFAULTS.accentColor)
  const tagline = ref<string | null>(null)
  const fontFamily = ref<string | null>(null)
  const portalWelcomeMessage = ref<string | null>(null)
  const poweredByText = ref<string | null>(null)
  const poweredByVisible = ref(false)

  // Track what context was fetched to avoid re-fetching
  const fetchedContext = ref<string | null>(null)

  // ── Computed ──
  const gradientStyle = computed(() =>
    `linear-gradient(135deg, ${primaryColor.value} 0%, ${darkenColor(primaryColor.value, 20)} 100%)`
  )

  const hasBranding = computed(() => loaded.value && (logoUrl.value || name.value !== DEFAULTS.platformName))

  // ── Actions ──

  /** Fetch branding based on authenticated user role */
  async function fetchForRole(role: string) {
    const ctx = `role:${role}`
    if (fetchedContext.value === ctx) return
    loading.value = true
    try {
      if (role === 'CLIENT_USER') {
        // Client portal: fetch agency branding via portal endpoint
        const { data } = await portalApi.getBranding()
        applyPortalBranding(data)
      } else {
        // Owner/Agency: show platform branding
        const { data } = await brandingApi.getPublicBranding('platform')
        applyPublicBranding(data)
      }
      fetchedContext.value = ctx
      loaded.value = true
    } catch {
      // On error, keep defaults
      resetToDefaults()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  /** Fetch public branding for unauthenticated pages (login, invite, reset) */
  async function fetchPublicBranding(agencyId?: string) {
    const ctx = agencyId ? `public:agency:${agencyId}` : 'public:platform'
    if (fetchedContext.value === ctx) return
    loading.value = true
    try {
      if (agencyId) {
        const { data } = await brandingApi.getPublicBranding('agency', agencyId)
        applyPublicBranding(data)
        // For agency-branded public pages, show powered-by
        poweredByVisible.value = true
        poweredByText.value = DEFAULTS.platformName
      } else {
        const { data } = await brandingApi.getPublicBranding('platform')
        applyPublicBranding(data)
      }
      fetchedContext.value = ctx
      loaded.value = true
    } catch {
      resetToDefaults()
      loaded.value = true
    } finally {
      loading.value = false
    }
  }

  async function fetchPublicBrandingBySlug(slug: string) {
    const normalizedSlug = slug.trim().toLowerCase()
    const ctx = `public:slug:${normalizedSlug}`
    if (!normalizedSlug) {
      resetToDefaults()
      loaded.value = true
      return false
    }
    if (fetchedContext.value === ctx) return true

    loading.value = true
    try {
      const { data } = await brandingApi.getPublicBrandingBySlug(normalizedSlug)
      applyPublicBranding(data)
      poweredByVisible.value = true
      poweredByText.value = DEFAULTS.platformName
      fetchedContext.value = ctx
      loaded.value = true
      return true
    } catch {
      resetToDefaults()
      loaded.value = true
      return false
    } finally {
      loading.value = false
    }
  }

  function applyPublicBranding(data: PublicBrandingResponse) {
    name.value = data.name || DEFAULTS.platformName
    logoUrl.value = data.logoUrl || null
    primaryColor.value = data.primaryColor || DEFAULTS.primaryColor
    secondaryColor.value = data.secondaryColor || DEFAULTS.secondaryColor
    tagline.value = data.tagline || null
    // Don't show powered-by for platform branding
    poweredByVisible.value = false
    poweredByText.value = null
  }

  function applyPortalBranding(data: PortalBrandingResponse) {
    name.value = data.agencyName || DEFAULTS.platformName
    logoUrl.value = data.logoUrl || null
    primaryColor.value = data.primaryColor || DEFAULTS.primaryColor
    secondaryColor.value = data.secondaryColor || DEFAULTS.secondaryColor
    accentColor.value = data.accentColor || DEFAULTS.accentColor
    fontFamily.value = data.fontFamily || null
    portalWelcomeMessage.value = data.portalWelcomeMessage || null
    poweredByText.value = data.poweredByText || null
    poweredByVisible.value = data.poweredByVisible ?? false
  }

  /** Apply current branding colors to Vuetify theme */
  function applyTheme(theme: ReturnType<typeof useTheme>) {
    const lightTheme = theme.themes.value.light
    if (!lightTheme) return
    lightTheme.colors.primary = primaryColor.value
    lightTheme.colors.secondary = secondaryColor.value
    lightTheme.colors.accent = accentColor.value
  }

  function resetToDefaults() {
    name.value = DEFAULTS.platformName
    logoUrl.value = null
    primaryColor.value = DEFAULTS.primaryColor
    secondaryColor.value = DEFAULTS.secondaryColor
    accentColor.value = DEFAULTS.accentColor
    tagline.value = null
    fontFamily.value = null
    portalWelcomeMessage.value = null
    poweredByText.value = null
    poweredByVisible.value = false
  }

  /** Reset branding state (on logout) */
  function $reset() {
    resetToDefaults()
    loaded.value = false
    fetchedContext.value = null
  }

  return {
    // state
    loaded,
    loading,
    name,
    logoUrl,
    primaryColor,
    secondaryColor,
    accentColor,
    tagline,
    fontFamily,
    portalWelcomeMessage,
    poweredByText,
    poweredByVisible,
    // computed
    gradientStyle,
    hasBranding,
    // actions
    fetchForRole,
    fetchPublicBranding,
    fetchPublicBrandingBySlug,
    applyTheme,
    $reset,
  }
})

/** Darken a hex color by a percentage (0-100) */
function darkenColor(hex: string, percent: number): string {
  try {
    const num = parseInt(hex.replace('#', ''), 16)
    const factor = 1 - percent / 100
    const r = Math.max(0, Math.round(((num >> 16) & 0xff) * factor))
    const g = Math.max(0, Math.round(((num >> 8) & 0xff) * factor))
    const b = Math.max(0, Math.round((num & 0xff) * factor))
    return `#${((r << 16) | (g << 8) | b).toString(16).padStart(6, '0')}`
  } catch {
    return hex
  }
}
