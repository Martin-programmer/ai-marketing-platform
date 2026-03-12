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
        <div class="d-flex ga-2">
          <v-btn color="deep-purple" variant="outlined" @click="showAiProposal = true" :disabled="!selectedClient">
            <v-icon start>mdi-robot</v-icon> AI Campaign Proposal
          </v-btn>
          <v-btn color="primary" @click="showCreateCampaign = true">
            <v-icon start>mdi-plus</v-icon> New Campaign
          </v-btn>
        </div>
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
            <v-btn v-if="rowItem(item).status === 'DRAFT'" size="small" variant="text" color="success" title="Publish" @click="store.publishCampaign(rowItem(item).id)">
              <v-icon>mdi-rocket-launch</v-icon>
            </v-btn>
            <v-btn v-if="rowItem(item).status === 'PUBLISHED'" size="small" variant="text" color="warning" title="Pause" @click="store.pauseCampaign(rowItem(item).id)">
              <v-icon>mdi-pause</v-icon>
            </v-btn>
            <v-btn v-if="rowItem(item).status === 'PAUSED'" size="small" variant="text" color="success" title="Resume" @click="store.resumeCampaign(rowItem(item).id)">
              <v-icon>mdi-play</v-icon>
            </v-btn>
          </template>

          <!-- Expanded row: Adsets -->
          <template #expanded-row="{ columns, item }">
            <tr>
              <td :colspan="columns.length" class="pa-4 bg-grey-lighten-4">
                <div class="d-flex justify-space-between align-center mb-2">
                  <h3>Adsets for "{{ rowItem(item).name }}"</h3>
                  <v-btn size="small" color="primary" variant="outlined" @click="openCreateAdset(rowItem(item).id)">
                    <v-icon start>mdi-plus</v-icon> New Adset
                  </v-btn>
                </div>
                <v-data-table
                  :headers="adsetHeaders"
                  :items="adsetMap[rowItem(item).id] || []"
                  item-value="id"
                  density="compact"
                  hover
                  show-expand
                  no-data-text="No adsets"
                  @click:row="(_e: any, row: any) => onAdsetClick(rowItem(row.item))"
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
                  <template #item.actions="{ item: adset }">
                    <v-btn
                      size="x-small"
                      variant="text"
                      color="info"
                      title="Reload Ads"
                      @click.stop="onAdsetClick(adset)"
                    >
                      <v-icon>mdi-refresh</v-icon>
                    </v-btn>
                    <v-btn
                      size="x-small"
                      variant="text"
                      color="primary"
                      title="Add Ad"
                      @click.stop="openCreateAd(adset.id)"
                    >
                      <v-icon>mdi-plus</v-icon>
                    </v-btn>
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

      <v-divider class="my-6" />

      <div class="d-flex align-center mb-4">
        <h2 class="text-h6">Budget Analysis</h2>
        <v-spacer />
        <v-btn
          color="deep-purple"
          :loading="dashStore.budgetLoading"
          @click="loadBudgetAnalysis"
        >
          <v-icon start>mdi-chart-areaspline</v-icon>
          Analyse Budget
        </v-btn>
      </div>

      <template v-if="dashStore.budgetAnalysis && !dashStore.budgetAnalysis.error">
        <v-card variant="tonal" color="deep-purple" class="mb-4">
          <v-card-title class="text-subtitle-1">
            <v-icon class="mr-2">mdi-robot</v-icon>AI Recommendation
          </v-card-title>
          <v-card-text class="text-body-2" style="white-space: pre-line">
            {{ dashStore.budgetAnalysis.narrative }}
          </v-card-text>
        </v-card>

        <v-row class="mb-4">
          <v-col cols="12" md="4">
            <v-card variant="outlined">
              <v-card-title class="text-subtitle-2">Monthly Pacing</v-card-title>
              <v-card-text>
                <div class="d-flex justify-space-between mb-2">
                  <span class="text-caption">Month Elapsed</span>
                  <span class="text-caption font-weight-bold">
                    {{ dashStore.budgetAnalysis.pacing?.pctMonthElapsed?.toFixed(0) }}%
                  </span>
                </div>
                <v-progress-linear
                  :model-value="dashStore.budgetAnalysis.pacing?.pctMonthElapsed || 0"
                  color="grey"
                  height="8"
                  rounded
                  class="mb-3"
                />
                <div class="d-flex justify-space-between mb-2">
                  <span class="text-caption">Budget Spent</span>
                  <span class="text-caption font-weight-bold">
                    {{ formatCurrency(dashStore.budgetAnalysis.pacing?.currentMonthSpend) }}
                  </span>
                </div>
                <v-progress-linear
                  :model-value="dashStore.budgetAnalysis.pacing?.projectedMonthSpend > 0
                    ? (dashStore.budgetAnalysis.pacing.currentMonthSpend / dashStore.budgetAnalysis.pacing.projectedMonthSpend) * 100
                    : 0"
                  :color="pacingColor"
                  height="8"
                  rounded
                  class="mb-3"
                />
                <v-chip :color="pacingColor" size="small" variant="tonal">
                  {{ dashStore.budgetAnalysis.pacing?.pacingStatus?.replace('_', ' ') }}
                </v-chip>
                <div class="text-caption text-medium-emphasis mt-2">
                  Projected: {{ formatCurrency(dashStore.budgetAnalysis.pacing?.projectedMonthSpend) }}
                  · Daily avg: {{ formatCurrency(dashStore.budgetAnalysis.pacing?.dailyAvg30d) }}
                </div>
              </v-card-text>
            </v-card>
          </v-col>

          <v-col cols="12" md="8">
            <v-card variant="outlined">
              <v-card-title class="text-subtitle-2">Day-of-Week Performance</v-card-title>
              <v-card-text>
                <v-table density="compact">
                  <thead>
                    <tr>
                      <th>Day</th>
                      <th class="text-end">Spend</th>
                      <th class="text-end">Conversions</th>
                      <th class="text-end">ROAS</th>
                      <th class="text-end">CTR %</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="d in dashStore.budgetAnalysis.dayOfWeek?.days || []"
                      :key="d.day"
                      :class="{
                        'bg-green-lighten-5': d.day === dashStore.budgetAnalysis.dayOfWeek?.bestDay,
                        'bg-red-lighten-5': d.day === dashStore.budgetAnalysis.dayOfWeek?.worstDay,
                      }"
                    >
                      <td>{{ d.day?.substring(0, 3) }}</td>
                      <td class="text-end">{{ formatCurrency(d.spend) }}</td>
                      <td class="text-end">{{ formatDecimal(d.conversions) }}</td>
                      <td class="text-end">{{ formatDecimal(d.roas) }}×</td>
                      <td class="text-end">{{ formatPercent(d.ctr) }}</td>
                    </tr>
                  </tbody>
                </v-table>
                <div class="text-caption text-medium-emphasis mt-2">
                  {{ dashStore.budgetAnalysis.dayOfWeek?.recommendation }}
                </div>
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>

        <v-card v-if="dashStore.budgetAnalysis.campaignRanking?.length" variant="outlined" class="mb-4">
          <v-card-title class="text-subtitle-2">Campaign Budget Ranking</v-card-title>
          <v-data-table
            :headers="budgetRankHeaders"
            :items="dashStore.budgetAnalysis.campaignRanking"
            density="compact"
          >
            <template #item.spend30d="{ item }">
              {{ formatCurrency(rowItem(item).spend30d) }}
            </template>
            <template #item.roas30d="{ item }">
              {{ formatDecimal(rowItem(item).roas30d) }}×
            </template>
            <template #item.dailyBudget="{ item }">
              {{ formatCurrency(rowItem(item).dailyBudget) }}
            </template>
            <template #item.suggestion="{ item }">
              <v-chip
                :color="rowItem(item).suggestion === 'INCREASE_BUDGET' ? 'success'
                  : rowItem(item).suggestion === 'DECREASE_BUDGET' ? 'warning'
                  : rowItem(item).suggestion === 'PAUSE_OR_RESTRUCTURE' ? 'error'
                  : 'grey'"
                size="x-small"
              >
                {{ rowItem(item).suggestion?.replace(/_/g, ' ') }}
              </v-chip>
            </template>
          </v-data-table>
        </v-card>

        <v-card v-if="dashStore.budgetAnalysis.diminishingReturns?.length" variant="outlined" class="mb-4">
          <v-card-title class="text-subtitle-2">
            <v-icon color="warning" class="mr-2" size="20">mdi-trending-down</v-icon>
            Diminishing Returns Detected
          </v-card-title>
          <v-card-text>
            <v-alert
              v-for="(dr, i) in dashStore.budgetAnalysis.diminishingReturns"
              :key="i"
              type="warning"
              variant="tonal"
              density="compact"
              class="mb-2"
            >
              <strong>{{ dr.entityType }} {{ dr.entityId?.substring(0, 8) }}…</strong>
              — {{ dr.description }}
            </v-alert>
          </v-card-text>
        </v-card>
      </template>

      <v-alert
        v-else-if="dashStore.budgetAnalysis?.error"
        type="warning"
        variant="tonal"
        class="mb-4"
      >
        {{ dashStore.budgetAnalysis.error }}
      </v-alert>
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

    <!-- AI Campaign Proposal Dialog (brief input) -->
    <v-dialog v-model="showAiProposal" max-width="560">
      <v-card title="AI Campaign Proposal" prepend-icon="mdi-robot">
        <v-card-text>
          <p class="text-body-2 mb-3">
            Claude will analyze this client's profile, creatives, historical performance
            and active campaigns to generate a full campaign structure.
          </p>
          <v-textarea
            v-model="aiBrief"
            label="Brief (optional)"
            placeholder="E.g. 'Focus on summer sale, target 25-45 age group, budget around $50/day'"
            rows="3"
            auto-grow
            variant="outlined"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showAiProposal = false">Cancel</v-btn>
          <v-btn
            color="deep-purple"
            :loading="store.proposalLoading"
            @click="onGenerateProposal"
          >
            <v-icon start>mdi-creation</v-icon> Generate
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- AI Proposal Result Dialog -->
    <v-dialog v-model="showProposalResult" max-width="900" scrollable>
      <v-card v-if="store.proposal">
        <v-card-title class="d-flex align-center ga-2">
          <v-icon color="deep-purple">mdi-robot</v-icon>
          {{ store.proposal.campaignName }}
          <v-chip size="small" color="info" class="ml-2">{{ store.proposal.objective }}</v-chip>
          <v-chip size="small" color="grey">{{ store.proposal.status }}</v-chip>
          <v-spacer />
          <v-btn icon size="small" @click="showProposalResult = false">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-card-title>

        <v-divider />

        <v-card-text style="max-height: 70vh; overflow-y: auto">
          <!-- Rationale -->
          <v-alert type="info" variant="tonal" density="compact" class="mb-4" icon="mdi-brain">
            <div class="font-weight-bold mb-1">AI Rationale</div>
            {{ store.proposal.rationale }}
          </v-alert>

          <!-- Budget & Estimated Results -->
          <div class="d-flex ga-4 mb-4">
            <v-card variant="tonal" color="success" class="pa-3 flex-grow-1">
              <div class="text-caption">Suggested Daily Budget</div>
              <div class="text-h6">${{ store.proposal.suggestedDailyBudget?.toFixed(2) }}</div>
            </v-card>
            <v-card variant="tonal" color="primary" class="pa-3 flex-grow-1">
              <div class="text-caption">Estimated Results</div>
              <div class="text-body-2">{{ store.proposal.estimatedResults }}</div>
            </v-card>
          </div>

          <!-- Warnings -->
          <v-alert
            v-for="(w, wi) in store.proposal.warnings"
            :key="wi"
            type="warning"
            variant="tonal"
            density="compact"
            class="mb-2"
          >{{ w }}</v-alert>

          <!-- Adsets tree -->
          <div v-for="(adset, ai) in store.proposal.adsets" :key="adset.adsetId" class="mb-4">
            <v-card variant="outlined">
              <v-card-title class="text-subtitle-1 d-flex align-center">
                <v-icon start size="small" color="blue">mdi-folder-outline</v-icon>
                {{ adset.name }}
                <v-chip size="x-small" class="ml-2">${{ adset.dailyBudget?.toFixed(2) }}/day</v-chip>
                <v-chip size="x-small" class="ml-1" color="grey">{{ adset.optimizationGoal }}</v-chip>
              </v-card-title>
              <v-card-subtitle>
                Targeting: {{ formatTargeting(adset.targetingJson) }}
              </v-card-subtitle>
              <v-divider />
              <v-list density="compact">
                <v-list-item v-for="(ad, adi) in adset.ads" :key="ad.adId" :title="ad.name">
                  <template #prepend>
                    <v-icon size="small" color="orange">mdi-bullhorn</v-icon>
                  </template>
                  <template #subtitle>
                    <div class="text-body-2 mt-1">
                      <strong>Headline:</strong> {{ ad.headline }}<br />
                      <strong>Primary:</strong> {{ ad.primaryText }}<br />
                      <strong>Description:</strong> {{ ad.description }}<br />
                      <v-chip size="x-small" class="mr-1">{{ ad.cta }}</v-chip>
                      <span v-if="ad.creativeAssetId" class="text-caption text-grey">
                        Asset: {{ ad.creativeAssetId.substring(0, 8) }}…
                      </span>
                      <v-chip v-else size="x-small" color="warning">No creative</v-chip>
                    </div>
                  </template>
                </v-list-item>
              </v-list>
            </v-card>
          </div>
        </v-card-text>

        <v-divider />

        <v-card-actions>
          <v-btn variant="text" @click="onRegenerate">
            <v-icon start>mdi-refresh</v-icon> Regenerate
          </v-btn>
          <v-spacer />
          <v-btn @click="showProposalResult = false">Close</v-btn>
          <v-btn
            color="success"
            :loading="publishLoading"
            @click="onApproveAndPublish"
          >
            <v-icon start>mdi-rocket-launch</v-icon> Approve & Publish to Meta
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, reactive } from 'vue'
import { useCampaignStore } from '@/stores/campaigns'
import { useClientStore } from '@/stores/clients'
import { useCreativeStore } from '@/stores/creatives'
import { useDashboardStore } from '@/stores/dashboard'

const store = useCampaignStore()
const clientStore = useClientStore()
const creativeStore = useCreativeStore()
const dashStore = useDashboardStore()
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

// AI Proposal state
const showAiProposal = ref(false)
const showProposalResult = ref(false)
const aiBrief = ref('')
const publishLoading = ref(false)

const pacingColor = computed(() => {
  const status = dashStore.budgetAnalysis?.pacing?.pacingStatus
  if (status === 'ON_TRACK') return 'success'
  if (status === 'OVERSPENDING') return 'error'
  return 'warning'
})

const budgetRankHeaders = [
  { title: 'Campaign', key: 'campaignName' },
  { title: 'Status', key: 'status', width: '100px' },
  { title: 'Spend (30d)', key: 'spend30d', align: 'end' as const },
  { title: 'ROAS', key: 'roas30d', align: 'end' as const },
  { title: 'Daily Budget', key: 'dailyBudget', align: 'end' as const },
  { title: 'Suggestion', key: 'suggestion' },
]

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

function rowItem<T = any>(item: any): T {
  return item && typeof item === 'object' && 'raw' in item ? item.raw as T : item as T
}

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
    dashStore.budgetAnalysis = null
    await Promise.all([
      store.fetchCampaigns(clientId),
      creativeStore.fetchPackages(clientId),
    ])
    // Pre-fetch adsets for each campaign
    for (const c of store.campaigns) {
      const data = await store.fetchAdsets(c.id)
      adsetMap[c.id] = data
      // Pre-fetch ads for each adset so expanded rows are populated immediately
      for (const adset of data) {
        const ads = await store.fetchAds(adset.id)
        adMap[adset.id] = ads
      }
    }
  }
}

async function loadBudgetAnalysis() {
  if (!selectedClient.value) return
  await dashStore.fetchBudgetAnalysis(selectedClient.value)
}

function formatCurrency(val: any): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    maximumFractionDigits: 2,
    minimumFractionDigits: 0,
  }).format(Number(val))
}

function formatPercent(val: any): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(Number(val)) + '%'
}

function formatDecimal(val: any): string {
  if (val == null) return '—'
  return new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(Number(val))
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

// AI Proposal handlers
async function onGenerateProposal() {
  if (!selectedClient.value) return
  try {
    await store.generateProposal(selectedClient.value, aiBrief.value)
    showAiProposal.value = false
    showProposalResult.value = true
  } catch (e: any) {
    // error is set in store
  }
}

async function onRegenerate() {
  if (!selectedClient.value) return
  showProposalResult.value = false
  showAiProposal.value = true
}

async function onApproveAndPublish() {
  if (!store.proposal) return
  publishLoading.value = true
  try {
    const result = await store.metaPublish(store.proposal.campaignId)
    if (result.status === 'PUBLISHED') {
      showProposalResult.value = false
      // Refresh campaigns list
      if (selectedClient.value) await store.fetchCampaigns(selectedClient.value)
    }
  } catch (e: any) {
    store.error = e.response?.data?.message || e.message
  } finally {
    publishLoading.value = false
  }
}

onMounted(() => clientStore.fetchClients())
</script>
