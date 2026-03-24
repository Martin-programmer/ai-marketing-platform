import api from './client'
import axios from 'axios'

export const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1'

export interface PublicBrandingResponse {
  name: string
  logoUrl: string | null
  primaryColor: string | null
  secondaryColor: string | null
  tagline: string | null
}

export interface PortalBrandingResponse {
  agencyName: string
  logoUrl: string | null
  primaryColor: string | null
  secondaryColor: string | null
  accentColor: string | null
  fontFamily: string | null
  portalWelcomeMessage: string | null
  poweredByText: string | null
  poweredByVisible: boolean
}

export interface AgencyBrandingResponse {
  slug: string | null
  logoUrl: string | null
  logoS3Key?: string | null
  primaryColor: string | null
  secondaryColor: string | null
  accentColor: string | null
  fontFamily: string | null
  companyEmail: string | null
  companyPhone: string | null
  companyWebsite: string | null
  companyAddress: string | null
  emailFooterText: string | null
  reportFooterText: string | null
  reportDisclaimer: string | null
  portalWelcomeMessage: string | null
  customCss: string | null
}

export interface UpdateBrandingRequest {
  slug?: string | null
  primaryColor?: string | null
  secondaryColor?: string | null
  accentColor?: string | null
  fontFamily?: string | null
  companyEmail?: string | null
  companyPhone?: string | null
  companyWebsite?: string | null
  companyAddress?: string | null
  emailFooterText?: string | null
  reportFooterText?: string | null
  reportDisclaimer?: string | null
  portalWelcomeMessage?: string | null
  customCss?: string | null
}

export const brandingApi = {
  /** Get agency branding settings (AGENCY_ADMIN only) */
  getAgencyBranding: () =>
    api.get<AgencyBrandingResponse>('/agency/branding'),

  /** Update agency branding settings (AGENCY_ADMIN only) */
  updateAgencyBranding: (data: UpdateBrandingRequest) =>
    api.put<AgencyBrandingResponse>('/agency/branding', data),

  /** Upload agency logo (AGENCY_ADMIN only, max 2MB, PNG/JPG/SVG) */
  uploadAgencyLogo: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post<{ logoUrl: string }>('/agency/branding/logo', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  checkSlug: (slug: string) =>
    api.get<{ available: boolean; suggestion?: string | null }>('/agency/branding/check-slug', {
      params: { slug },
    }),

  /** Get public branding (no auth required) */
  getPublicBranding: (context: 'agency' | 'platform', agencyId?: string) => {
    const params: Record<string, string> = { context }
    if (agencyId) params.agencyId = agencyId
    return axios.get<PublicBrandingResponse>(`${API_BASE}/public/branding`, { params })
  },

  getPublicBrandingBySlug: (slug: string) =>
    axios.get<PublicBrandingResponse>(`${API_BASE}/public/branding/agency/${encodeURIComponent(slug)}`),
}

export default brandingApi
