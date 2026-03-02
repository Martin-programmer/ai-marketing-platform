<template>
  <div>
    <h1 class="mb-4">Campaigns</h1>

    <v-select
      v-model="selectedClient"
      :items="clientStore.clients"
      item-title="name"
      item-value="id"
      label="Select Client"
      variant="outlined"
      density="compact"
      class="mb-4"
      style="max-width: 400px"
      @update:model-value="onClientChange"
    />

    <v-progress-linear v-if="store.loading" indeterminate color="primary" class="mb-4" />

    <v-alert v-if="store.error" type="error" class="mb-4">{{ store.error }}</v-alert>

    <v-alert v-if="!selectedClient" type="info" class="mb-4">
      Select a client to view campaigns.
    </v-alert>

    <template v-if="selectedClient">
      <div class="d-flex justify-space-between align-center mb-3">
        <h2>Campaigns</h2>
        <v-btn color="primary" @click="showCreateCampaign = true">
          <v-icon start>mdi-plus</v-icon> New Campaign
        </v-btn>
      </div>

      <v-card>
        <v-data-table
          :headers="campaignHeaders"
          :items="store.campaigns"
          :loading="store.loading"
          item-value="id"
          hover
          show-expand
          no-data-text="No campaigns yet"
        >
          <template #item.status="{ item }">
            <v-chip :color="campaignStatusColor(item.status)" size="small">
              {{ item.status }}
            </v-chip>
          </template>
          <template #item.createdAt="{ item }">
            {{ new Date(item.createdAt).toLocaleDateString() }}
          </template>
          <template #item.actions="{ item }">
            <v-btn v-if="item.status === 'DRAFT'" size="small" variant="text" color="success" title="Publish" @click="store.publishCampaign(item.id)">
              <v-icon>mdi-rocket-launch</v-icon>
            </v-btn>
            <v-btn v-if="item.status === 'PUBLISHED'" size="small" variant="text" color="warning" title="Pause" @click="store.pauseCampaign(item.id)">
              <v-icon>mdi-pause</v-icon>
            </v-btn>
            <v-btn v-if="item.status === 'PAUSED'" size="small" variant="text" color="success" title="Resume" @click="store.resumeCampaign(item.id)">
              <v-icon>mdi-play</v-icon>
            </v-btn>
          </template>

          <!-- Expanded row: Adsets -->
          <template #expanded-row="{ columns, item }">
            <tr>
              <td :colspan="columns.length" class="pa-4 bg-grey-lighten-4">
                <div class="d-flex justify-space-between align-center mb-2">
                  <h3>Adsets for "{{ item.name }}"</h3>
                  <v-btn size="small" color="primary" variant="outlined" @click="openCreateAdset(item.id)">
                    <v-icon start>mdi-plus</v-icon> New Adset
                  </v-btn>
                </div>
                <v-data-table
                  :headers="adsetHeaders"
                  :items="adsetMap[item.id] || []"
                  item-value="id"
                  density="compact"
                  hover
                  show-expand
                  no-data-text="No adsets"
                  @click:row="(_e: any, row: any) => onAdsetClick(row.item)"
                >
                  <template #item.dailyBudget="{ item: adset }">
                    {{ adset.dailyBudget?.toFixed(2) ?? '—' }} BGN
                  </template>
                  <template #item.targetingJson="{ item: adset }">
                    <v-tooltip location="top">
                      <template #activator="{ props: tp }">
                        <v-chip v-bind="tp" size="small" variant="outlined">
                          {{ formatTargeting(adset.targetingJson) }}
                        </v-chip>
                      </template>
                      <pre style="max-width:400px;white-space:pre-wrap">{{ typeof adset.targetingJson === 'string' ? adset.targetingJson : JSON.stringify(adset.targetingJson, null, 2) }}</pre>
                    </v-tooltip>
                  </template>
                  <template #item.status="{ item: adset }">
                    <v-chip :color="campaignStatusColor(adset.status)" size="small">{{ adset.status }}</v-chip>
                  </template>

                  <!-- Expanded adset row: Ads -->
                  <template #expanded-row="{ columns: adCols, item: adset }">
                    <tr>
                      <td :colspan="adCols.length" class="pa-4 bg-grey-lighten-5">
                        <div class="d-flex justify-space-between align-center mb-2">
                          <h4>Ads for "{{ adset.name }}"</h4>
                          <v-btn size="x-small" color="primary" variant="outlined" @click="openCreateAd(adset.id)">
                            <v-icon start>mdi-plus</v-icon> New Ad
                          </v-btn>
                        </div>
                        <v-data-table
                          :headers="adHeaders"
                          :items="adMap[adset.id] || []"
                          item-value="id"
                          density="compact"
                          hover
                          no-data-text="No ads"
                        >
                          <template #item.status="{ item: ad }">
                            <v-chip :color="campaignStatusColor(ad.status)" size="small">{{ ad.status }}</v-chip>
                          </template>
                        </v-data-table>
                      </td>
                    </tr>
                  </template>
                </v-data-table>
              </td>
            </tr>
          </template>
        </v-data-table>
      </v-card>
    </template>

    <!-- Create Campaign Dialog -->
    <v-dialog v-model="showCreateCampaign" max-width="500">
      <v-card title="New Campaign">
        <v-card-text>
          <v-text-field v-model="campaignForm.name" label="Name" required />
          <v-select
            v-model="campaignForm.objective"
            :items="['SALES', 'LEADS', 'TRAFFIC', 'AWARENESS']"
            label="Objective"
            required
          />
          <v-text-field v-model="campaignForm.platform" label="Platform" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreateCampaign = false">Cancel</v-btn>
          <v-btn color="primary" @click="onCreateCampaign">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Create Adset Dialog -->
    <v-dialog v-model="showCreateAdset" max-width="500">
      <v-card title="New Adset">
        <v-card-text>
          <v-text-field v-model="adsetForm.name" label="Name" required />
          <v-text-field v-model.number="adsetForm.dailyBudget" label="Daily Budget" type="number" prefix="BGN" />
          <v-textarea v-model="adsetForm.targetingJson" label="Targeting (JSON)" rows="3" auto-grow placeholder='{"age_min": 25, "age_max": 45}' />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreateAdset = false">Cancel</v-btn>
          <v-btn color="primary" @click="onCreateAdset">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Create Ad Dialog -->
    <v-dialog v-model="showCreateAd" max-width="500">
      <v-card title="New Ad">
        <v-card-text>
          <v-text-field v-model="adForm.name" label="Name" required />
          <v-select
            v-model="adForm.creativePackageItemId"
            :items="creativeStore.packages"
            item-title="name"
            item-value="id"
            label="Creative Package"
            clearable
            :no-data-text="'No packages available'"
          >
            <template #prepend-item v-if="creativeStore.packages.length === 0">
              <v-list-item>
                <v-list-item-title class="text-grey">
                  No creative packages found.
                </v-list-item-title>
                <v-list-item-subtitle>
                  <v-btn size="small" color="primary" variant="text" to="/creatives">
                    <v-icon start>mdi-plus</v-icon> Create a package first
                  </v-btn>
                </v-list-item-subtitle>
              </v-list-item>
            </template>
            <template #item="{ item, props: itemProps }">
              <v-list-item v-bind="itemProps">
                <template #subtitle>
                  <v-chip size="x-small" :color="item.status === 'APPROVED' ? 'success' : item.status === 'DRAFT' ? 'grey' : 'info'" class="mr-1">
                    {{ item.status }}
                  </v-chip>
                  {{ item.objective || '' }}
                </template>
              </v-list-item>
            </template>
          </v-select>
          <v-alert v-if="creativeStore.packages.length === 0 && selectedClient" type="warning" density="compact" variant="tonal" class="mt-2">
            No creative packages available for this client.
            <v-btn size="small" variant="text" color="primary" to="/creatives">Go to Creatives</v-btn>
            to create one first.
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreateAd = false">Cancel</v-btn>
          <v-btn color="primary" @click="onCreateAd">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useCampaignStore } from '@/stores/campaigns'
import { useClientStore } from '@/stores/clients'
import { useCreativeStore } from '@/stores/creatives'

const store = useCampaignStore()
const clientStore = useClientStore()
const creativeStore = useCreativeStore()
const selectedClient = ref<string | null>(null)

const adsetMap = reactive<Record<string, any[]>>({})
const adMap = reactive<Record<string, any[]>>({})

const showCreateCampaign = ref(false)
const showCreateAdset = ref(false)
const showCreateAd = ref(false)
const currentCampaignId = ref('')
const currentAdsetId = ref('')

const campaignForm = ref({ name: '', objective: 'SALES', platform: 'META' })
const adsetForm = ref({ name: '', dailyBudget: 0, targetingJson: '' })
const adForm = ref({ name: '', creativePackageItemId: '' })

const campaignHeaders = [
  { title: 'Name', key: 'name' },
  { title: 'Objective', key: 'objective' },
  { title: 'Platform', key: 'platform' },
  { title: 'Status', key: 'status' },
  { title: 'Created', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false },
]

const adsetHeaders = [
  { title: 'Name', key: 'name' },
  { title: 'Daily Budget', key: 'dailyBudget' },
  { title: 'Targeting', key: 'targetingJson' },
  { title: 'Status', key: 'status' },
  { title: 'Actions', key: 'actions', sortable: false },
]

const adHeaders = [
  { title: 'Name', key: 'name' },
  { title: 'Status', key: 'status' },
]

function campaignStatusColor(status: string) {
  const map: Record<string, string> = { DRAFT: 'grey', PUBLISHED: 'success', PAUSED: 'warning', ARCHIVED: 'error' }
  return map[status] || 'grey'
}

function formatTargeting(json: any): string {
  if (!json) return '—'
  const str = typeof json === 'string' ? json : JSON.stringify(json)
  return str.length > 50 ? str.substring(0, 50) + '…' : str
}

async function onClientChange(clientId: string) {
  if (clientId) {
    store.selectedClientId = clientId
    await Promise.all([
      store.fetchCampaigns(clientId),
      creativeStore.fetchPackages(clientId),
    ])
    // Pre-fetch adsets for each campaign
    for (const c of store.campaigns) {
      const data = await store.fetchAdsets(c.id)
      adsetMap[c.id] = data
    }
  }
}

async function onAdsetClick(adset: any) {
  const data = await store.fetchAds(adset.id)
  adMap[adset.id] = data
}

function openCreateAdset(campaignId: string) {
  currentCampaignId.value = campaignId
  adsetForm.value = { name: '', dailyBudget: 0, targetingJson: '' }
  showCreateAdset.value = true
}

function openCreateAd(adsetId: string) {
  currentAdsetId.value = adsetId
  adForm.value = { name: '', creativePackageItemId: '' }
  showCreateAd.value = true
}

async function onCreateCampaign() {
  if (!selectedClient.value) return
  await store.createCampaign(selectedClient.value, campaignForm.value)
  showCreateCampaign.value = false
  campaignForm.value = { name: '', objective: 'SALES', platform: 'META' }
}

async function onCreateAdset() {
  const data = await store.createAdset(currentCampaignId.value, adsetForm.value)
  const key = currentCampaignId.value
  if (!adsetMap[key]) adsetMap[key] = []
  adsetMap[key]!.push(data)
  showCreateAdset.value = false
}

async function onCreateAd() {
  const payload: any = { name: adForm.value.name }
  if (adForm.value.creativePackageItemId) payload.creativePackageItemId = adForm.value.creativePackageItemId
  const data = await store.createAd(currentAdsetId.value, payload)
  const key = currentAdsetId.value
  if (!adMap[key]) adMap[key] = []
  adMap[key]!.push(data)
  showCreateAd.value = false
}

onMounted(() => clientStore.fetchClients())
</script>
