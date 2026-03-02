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

export const useCreativeStore = defineStore('creatives', () => {
  const assets = ref<CreativeAsset[]>([])
  const packages = ref<CreativePackage[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

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

  async function submitPackage(packageId: string) {
    const { data } = await api.post(`/creative-packages/${packageId}/submit`)
    const idx = packages.value.findIndex(p => p.id === packageId)
    if (idx >= 0) packages.value[idx] = data
  }

  async function approvePackage(packageId: string) {
    const { data } = await api.post(`/creative-packages/${packageId}/approve`)
    const idx = packages.value.findIndex(p => p.id === packageId)
    if (idx >= 0) packages.value[idx] = data
  }

  return { assets, packages, loading, error, fetchAssets, fetchPackages, createPackage, submitPackage, approvePackage }
})
