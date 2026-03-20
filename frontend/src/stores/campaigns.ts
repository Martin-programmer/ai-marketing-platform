import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

export interface Campaign {
  id: string
  agencyId: string
  clientId: string
  platform: string
  metaCampaignId: string | null
  name: string
  objective: string
  budgetType: string
  dailyBudget: number | null
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface Adset {
  id: string
  agencyId: string
  clientId: string
  campaignId: string
  metaAdsetId: string | null
  name: string
  targetingJson: any
  dailyBudget: number
  optimizationGoal: string
  conversionEvent: string | null
  status: string
  startDate: string | null
  endDate: string | null
  createdAt: string
  updatedAt: string
}

export interface Ad {
  id: string
  agencyId: string
  clientId: string
  adsetId: string
  metaAdId: string | null
  name: string
  creativePackageItemId: string | null
  creativeAssetId: string | null
  copyVariantId: string | null
  primaryText: string
  headline: string
  description: string
  cta: string
  destinationUrl: string
  status: string
  createdAt: string
  updatedAt: string
}

export interface ProposedAd {
  adId: string
  name: string
  creativePackageItemId: string | null
  creativeAssetId: string | null
  copyVariantId: string | null
  primaryText: string
  headline: string
  description: string
  cta: string
  url: string
}

export interface ProposedAdset {
  adsetId: string
  name: string
  dailyBudget: number
  targetingJson: string
  optimizationGoal: string
  ads: ProposedAd[]
}

export interface CampaignProposal {
  campaignId: string
  campaignName: string
  objective: string
  budgetType: string
  campaignDailyBudget: number | null
  platform: string
  status: string
  rationale: string
  suggestedDailyBudget: number
  estimatedResults: string
  warnings: string[]
  adsets: ProposedAdset[]
}

export interface CampaignAiAnalyzeSuggestion {
  id: string
  suggestionType: string
  rationale: string
  scopeType: string
  scopeId: string
}

export interface CampaignAiAnalyzeResult {
  findingsCount: number
  suggestionsCreated: number
  message?: string
  suggestions: CampaignAiAnalyzeSuggestion[]
}

export const useCampaignStore = defineStore('campaigns', () => {
  const campaigns = ref<Campaign[]>([])
  const adsets = ref<Adset[]>([])
  const ads = ref<Ad[]>([])
  const selectedClientId = ref<string | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)
  const proposalLoading = ref(false)
  const proposal = ref<CampaignProposal | null>(null)

  async function fetchCampaigns(clientId: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get(`/clients/${clientId}/campaigns`)
      campaigns.value = data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  async function createCampaign(clientId: string, payload: {
    name: string
    objective: string
    platform?: string
    budgetType?: string
    dailyBudget?: number | null
  }) {
    const { data } = await api.post(`/clients/${clientId}/campaigns`, payload)
    campaigns.value.push(data)
    return data
  }

  async function publishCampaign(campaignId: string) {
    const { data } = await api.post(`/campaigns/${campaignId}/publish`)
    // Backend now returns { status, campaignId, steps, ... }
    const idx = campaigns.value.findIndex(c => c.id === campaignId)
    const campaign = idx >= 0 ? campaigns.value[idx] : undefined
    if (campaign && data.status) {
      campaign.status = data.status as string
    }
    return data
  }

  async function pauseCampaign(campaignId: string) {
    const { data } = await api.post(`/campaigns/${campaignId}/pause`)
    const idx = campaigns.value.findIndex(c => c.id === campaignId)
    if (idx >= 0) campaigns.value[idx] = data
  }

  async function resumeCampaign(campaignId: string) {
    const { data } = await api.post(`/campaigns/${campaignId}/resume`)
    const idx = campaigns.value.findIndex(c => c.id === campaignId)
    if (idx >= 0) campaigns.value[idx] = data
  }

  async function fetchAdsets(campaignId: string) {
    const { data } = await api.get(`/campaigns/${campaignId}/adsets`)
    adsets.value = data
    return data
  }

  async function createAdset(campaignId: string, payload: { name: string; dailyBudget: number; targetingJson?: any }) {
    const { data } = await api.post(`/campaigns/${campaignId}/adsets`, payload)
    adsets.value.push(data)
    return data
  }

  async function fetchAds(adsetId: string) {
    const { data } = await api.get(`/adsets/${adsetId}/ads`)
    ads.value = data
    return data
  }

  async function createAd(adsetId: string, payload: { name: string; creativePackageItemId?: string }) {
    const { data } = await api.post(`/adsets/${adsetId}/ads`, payload)
    ads.value.push(data)
    return data
  }

  async function patchCampaign(campaignId: string, payload: { name?: string; status?: string }) {
    const { data } = await api.patch(`/campaigns/${campaignId}`, payload)
    const idx = campaigns.value.findIndex(c => c.id === campaignId)
    if (idx >= 0) campaigns.value[idx] = data
    return data
  }

  async function patchAdset(campaignId: string, adsetId: string, payload: {
    name?: string
    dailyBudget?: number | null
    targetingJson?: string
    optimizationGoal?: string
    conversionEvent?: string | null
    status?: string
  }) {
    const { data } = await api.patch(`/campaigns/${campaignId}/adsets/${adsetId}`, payload)
    const idx = adsets.value.findIndex(a => a.id === adsetId)
    if (idx >= 0) adsets.value[idx] = data
    return data
  }

  async function patchAd(campaignId: string, adsetId: string, adId: string, payload: {
    name?: string
    primaryText?: string
    headline?: string
    description?: string
    ctaType?: string
    destinationUrl?: string
    status?: string
  }) {
    const { data } = await api.patch(`/campaigns/${campaignId}/adsets/${adsetId}/ads/${adId}`, payload)
    const idx = ads.value.findIndex(a => a.id === adId)
    if (idx >= 0) ads.value[idx] = data
    return data
  }

  async function generateProposal(clientId: string, payload: {
    brief: string
    budgetType?: string
    dailyBudget?: number | null
  }) {
    proposalLoading.value = true
    error.value = null
    proposal.value = null
    try {
      const { data } = await api.post(`/clients/${clientId}/campaigns/ai-propose`, payload)
      proposal.value = data
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      proposalLoading.value = false
    }
  }

  async function metaPublish(campaignId: string) {
    const { data } = await api.post(`/campaigns/${campaignId}/meta-publish`)
    // Refresh the campaign in the list
    const idx = campaigns.value.findIndex(c => c.id === campaignId)
    const campaign = idx >= 0 ? campaigns.value[idx] : undefined
    if (campaign && data.status) {
      campaign.status = data.status as string
    }
    return data
  }

  async function aiAnalyzeCampaign(campaignId: string): Promise<CampaignAiAnalyzeResult> {
    const { data } = await api.post(`/campaigns/${campaignId}/ai-analyze`)
    return data
  }

  return {
    campaigns, adsets, ads, selectedClientId, loading, error,
    proposal, proposalLoading,
    fetchCampaigns, createCampaign, publishCampaign, pauseCampaign, resumeCampaign,
    fetchAdsets, createAdset, fetchAds, createAd,
    patchCampaign, patchAdset, patchAd,
    generateProposal, metaPublish, aiAnalyzeCampaign,
  }
})
