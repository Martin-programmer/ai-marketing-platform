import { defineStore } from 'pinia'
import { ref } from 'vue'
import api from '@/api/client'

export interface CreativeAsset {
  id: string
  agencyId: string
  clientId: string
  assetType: string
  s3Bucket: string
  s3Key: string
  originalFilename: string
  mimeType: string
  sizeBytes: number
  widthPx: number | null
  heightPx: number | null
  durationMs: number | null
  checksumSha256: string | null
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
  thumbnailUrl?: string
}

export interface CreativeAnalysis {
  id: string
  agencyId: string
  clientId: string
  creativeAssetId: string
  analysisJson: string
  qualityScore: number | null
  createdAt: string
}

export interface CopyVariant {
  id: string
  agencyId: string
  clientId: string
  creativeAssetId: string
  language: string
  primaryText: string
  headline: string
  description: string | null
  cta: string
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface CreativePackage {
  id: string
  agencyId: string
  clientId: string
  name: string
  objective: string | null
  status: string
  notes: string | null
  createdBy: string
  approvedBy: string | null
  createdAt: string
  approvedAt: string | null
}

export interface PackageItem {
  id: string
  agencyId: string
  clientId: string
  packageId: string
  creativeAssetId: string
  copyVariantId: string
  ctaType: string | null
  destinationUrl: string | null
  weight: number
  createdAt: string
  creativeAsset: CreativeAsset | null
  copyVariant: CopyVariant | null
  qualityScore: number | null
}

export const useCreativeStore = defineStore('creatives', () => {
  const assets = ref<CreativeAsset[]>([])
  const packages = ref<CreativePackage[]>([])
  const analysisByAsset = ref<Record<string, CreativeAnalysis | null>>({})
  const analysisLoaded = ref<Record<string, boolean>>({})
  const analysisLoadingByAsset = ref<Record<string, boolean>>({})
  const copyVariantsByAsset = ref<Record<string, CopyVariant[]>>({})
  const copyVariantsLoaded = ref<Record<string, boolean>>({})
  const copyVariantsLoadingByAsset = ref<Record<string, boolean>>({})
  const packageItemsByPackage = ref<Record<string, PackageItem[]>>({})
  const packageItemsLoadingByPackage = ref<Record<string, boolean>>({})
  const analyzeRunningByAsset = ref<Record<string, boolean>>({})
  const generateCopyRunningByAsset = ref<Record<string, boolean>>({})
  const copyVariantActionLoading = ref<Record<string, boolean>>({})
  const loading = ref(false)
  const error = ref<string | null>(null)

  function resetInsightState() {
    analysisByAsset.value = {}
    analysisLoaded.value = {}
    analysisLoadingByAsset.value = {}
    copyVariantsByAsset.value = {}
    copyVariantsLoaded.value = {}
    copyVariantsLoadingByAsset.value = {}
    packageItemsByPackage.value = {}
    packageItemsLoadingByPackage.value = {}
    analyzeRunningByAsset.value = {}
    generateCopyRunningByAsset.value = {}
    copyVariantActionLoading.value = {}
  }

  function upsertCopyVariant(variant: CopyVariant) {
    const assetId = variant.creativeAssetId
    const existing = copyVariantsByAsset.value[assetId] ?? []
    const index = existing.findIndex((item) => item.id === variant.id)
    if (index >= 0) {
      copyVariantsByAsset.value[assetId] = [
        ...existing.slice(0, index),
        variant,
        ...existing.slice(index + 1),
      ]
      return
    }

    copyVariantsByAsset.value[assetId] = [variant, ...existing]
  }

  async function fetchAssets(clientId: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get(`/clients/${clientId}/creatives`)
      assets.value = data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  async function fetchPackages(clientId: string) {
    loading.value = true
    error.value = null
    try {
      const { data } = await api.get(`/clients/${clientId}/creative-packages`)
      packages.value = data
    } catch (e: any) {
      error.value = e.message
    } finally {
      loading.value = false
    }
  }

  async function createPackage(clientId: string, payload: { name: string; objective?: string }) {
    const { data } = await api.post(`/clients/${clientId}/creative-packages`, payload)
    packages.value.push(data)
    return data
  }

  async function updatePackage(packageId: string, payload: { name: string; objective?: string; notes?: string }) {
    const { data } = await api.put(`/creative-packages/${packageId}`, payload)
    const idx = packages.value.findIndex((item) => item.id === packageId)
    if (idx >= 0) packages.value[idx] = data
    return data
  }

  async function submitPackage(packageId: string) {
    const { data } = await api.post(`/creative-packages/${packageId}/submit`)
    const idx = packages.value.findIndex(p => p.id === packageId)
    if (idx >= 0) packages.value[idx] = data
    return data
  }

  async function approvePackage(packageId: string) {
    const { data } = await api.post(`/creative-packages/${packageId}/approve`)
    const idx = packages.value.findIndex(p => p.id === packageId)
    if (idx >= 0) packages.value[idx] = data
    return data
  }

  async function rejectPackage(packageId: string) {
    const { data } = await api.post(`/creative-packages/${packageId}/reject`)
    const idx = packages.value.findIndex(p => p.id === packageId)
    if (idx >= 0) packages.value[idx] = data
    return data
  }

  async function fetchPackageItems(packageId: string, force = false) {
    if (!force && packageItemsByPackage.value[packageId]) {
      return packageItemsByPackage.value[packageId]
    }
    packageItemsLoadingByPackage.value[packageId] = true
    try {
      const { data } = await api.get(`/creative-packages/${packageId}/items`)
      packageItemsByPackage.value[packageId] = data
      return data
    } finally {
      packageItemsLoadingByPackage.value[packageId] = false
    }
  }

  async function addPackageItem(packageId: string, payload: {
    creativeAssetId: string
    copyVariantId: string
    ctaType: string
    destinationUrl: string
    weight: number
  }) {
    const { data } = await api.post(`/creative-packages/${packageId}/items`, payload)
    packageItemsByPackage.value[packageId] = [...(packageItemsByPackage.value[packageId] ?? []), data]
    return data
  }

  async function deletePackageItem(packageId: string, itemId: string) {
    await api.delete(`/creative-packages/${packageId}/items/${itemId}`)
    packageItemsByPackage.value[packageId] = (packageItemsByPackage.value[packageId] ?? []).filter((item) => item.id !== itemId)
  }

  async function fetchAnalysis(assetId: string, force = false) {
    if (!force && analysisLoaded.value[assetId]) {
      return analysisByAsset.value[assetId] ?? null
    }

    analysisLoadingByAsset.value[assetId] = true
    error.value = null
    try {
      const response = await api.get(`/creatives/${assetId}/analysis`, {
        validateStatus: (status) => (status >= 200 && status < 300) || status === 204,
      })
      analysisByAsset.value[assetId] = response.status === 204 ? null : response.data
      analysisLoaded.value[assetId] = true
      return analysisByAsset.value[assetId]
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      analysisLoadingByAsset.value[assetId] = false
    }
  }

  async function fetchCopyVariants(assetId: string, force = false) {
    if (!force && copyVariantsLoaded.value[assetId]) {
      return copyVariantsByAsset.value[assetId] ?? []
    }

    copyVariantsLoadingByAsset.value[assetId] = true
    error.value = null
    try {
      const { data } = await api.get(`/creatives/${assetId}/copy-variants`)
      copyVariantsByAsset.value[assetId] = data
      copyVariantsLoaded.value[assetId] = true
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      copyVariantsLoadingByAsset.value[assetId] = false
    }
  }

  async function analyzeAsset(assetId: string) {
    analyzeRunningByAsset.value[assetId] = true
    error.value = null
    try {
      await api.post(`/creatives/${assetId}/analyze`)
      return await fetchAnalysis(assetId, true)
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      analyzeRunningByAsset.value[assetId] = false
    }
  }

  async function generateCopy(assetId: string) {
    generateCopyRunningByAsset.value[assetId] = true
    error.value = null
    try {
      await api.post(`/creatives/${assetId}/generate-copy`)
      return await fetchCopyVariants(assetId, true)
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      generateCopyRunningByAsset.value[assetId] = false
    }
  }

  async function createCopyVariant(clientId: string, payload: {
    assetId?: string | null
    primaryText: string
    headline: string
    description?: string | null
    cta?: string
    tone?: string
    language?: string
  }) {
    const { data } = await api.post(`/clients/${clientId}/copy-variants`, {
      clientId,
      assetId: payload.assetId,
      primaryText: payload.primaryText,
      headline: payload.headline,
      description: payload.description,
      cta: payload.cta,
      tone: payload.tone,
      language: payload.language,
    })
    upsertCopyVariant(data)
    return data
  }

  async function updateCopyVariantStatus(variantId: string, nextStatus: 'APPROVED' | 'REJECTED') {
    copyVariantActionLoading.value[variantId] = true
    error.value = null
    try {
      const action = nextStatus === 'APPROVED' ? 'approve' : 'reject'
      const { data } = await api.post(`/copy-variants/${variantId}/${action}`)
      upsertCopyVariant(data)
      return data
    } catch (e: any) {
      error.value = e.response?.data?.message || e.message
      throw e
    } finally {
      copyVariantActionLoading.value[variantId] = false
    }
  }

  return {
    assets,
    packages,
    analysisByAsset,
    analysisLoaded,
    analysisLoadingByAsset,
    copyVariantsByAsset,
    copyVariantsLoaded,
    copyVariantsLoadingByAsset,
    packageItemsByPackage,
    packageItemsLoadingByPackage,
    analyzeRunningByAsset,
    generateCopyRunningByAsset,
    copyVariantActionLoading,
    loading,
    error,
    resetInsightState,
    fetchAssets,
    fetchPackages,
    createPackage,
    updatePackage,
    submitPackage,
    approvePackage,
    rejectPackage,
    fetchPackageItems,
    addPackageItem,
    deletePackageItem,
    fetchAnalysis,
    fetchCopyVariants,
    analyzeAsset,
    generateCopy,
    createCopyVariant,
    updateCopyVariantStatus,
  }
})
