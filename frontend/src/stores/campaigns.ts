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
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface Adset {
  id: string
  agencyId: string
  campaignId: string
  metaAdsetId: string | null
  name: string
  targetingJson: any
  dailyBudget: number
  bidAmount: number | null
  status: string
  createdAt: string
  updatedAt: string
}

export interface Ad {
  id: string
  agencyId: string
  adsetId: string
  metaAdId: string | null
  name: string
  creativePackageId: string | null
  status: string
  createdAt: string
  updatedAt: string
}

export interface ProposedAd {
  adId: string
  name: string
  creativeAssetId: string | null
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
  platform: string
  status: string
  rationale: string
  suggestedDailyBudget: number
  estimatedResults: string
  warnings: string[]
  adsets: ProposedAdset[]
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

  async function createCampaign(clientId: string, payload: { name: string; objective: string; platform?: string }) {
    const { data } = await api.post(`/clients/${clientId}/campaigns`, payload)
    campaigns.value.push(data)
    return data
  }

  async function publishCampaign(campaignId: string) {
    const { data } = await api.post(`/campaigns/${campaignId}/publish`)
    const idx = campaigns.value.findIndex(c => c.id === campaignId)
    if (idx >= 0) campaigns.value[idx] = data
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

  async function generateProposal(clientId: string, brief: string) {
    proposalLoading.value = true
    error.value = null
    proposal.value = null
    try {
      const { data } = await api.post(`/clients/${clientId}/campaigns/ai-propose`, { brief })
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
    const existing = idx >= 0 ? campaigns.value[idx] : null
    if (existing && data.status === 'PUBLISHED') {
      campaigns.value[idx] = { ...existing, status: 'PUBLISHED' }
    }
    return data
  }

  return {
    campaigns, adsets, ads, selectedClientId, loading, error,
    proposal, proposalLoading,
    fetchCampaigns, createCampaign, publishCampaign, pauseCampaign, resumeCampaign,
    fetchAdsets, createAdset, fetchAds, createAd,
    generateProposal, metaPublish,
  }
})
