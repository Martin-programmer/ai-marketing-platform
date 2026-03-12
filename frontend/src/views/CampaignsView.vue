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
      <div class="d-flex justify-space-between align-center mb-3 flex-wrap ga-3">
        <h2>Campaigns</h2>
        <div class="d-flex ga-2">
          <v-btn color="deep-purple" size="large" elevation="3" @click="openAiProposalDialog" :disabled="!selectedClient">
            <v-icon start>mdi-robot-excited-outline</v-icon> AI Campaign Proposal
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

    <v-overlay
      :model-value="store.proposalLoading"
      class="align-center justify-center"
      persistent
      contained
    >
      <v-card min-width="360" max-width="520" class="pa-4 text-center">
        <v-progress-circular indeterminate color="deep-purple" size="56" width="5" class="mb-4" />
        <div class="text-h6 mb-2">AI генерира кампания...</div>
        <div class="text-body-2 text-medium-emphasis">15-30 секунди</div>
      </v-card>
    </v-overlay>

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
            label="Опишете целта на кампанията (опционално)"
            placeholder="Напр. 'Лятна промоция, жени 25-45, бюджет около $50 на ден'"
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
    <v-dialog v-model="showProposalResult" max-width="1280" scrollable>
      <v-card v-if="draftProposal">
        <v-card-title class="d-flex align-center ga-2 flex-wrap">
          <v-icon color="deep-purple">mdi-robot</v-icon>
          <span>Campaign Proposal Preview</span>
          <v-chip size="small" color="info">{{ draftProposal.objective }}</v-chip>
          <v-chip size="small" color="grey">{{ draftProposal.status }}</v-chip>
          <v-spacer />
          <v-btn icon size="small" @click="showProposalResult = false">
            <v-icon>mdi-close</v-icon>
          </v-btn>
        </v-card-title>

        <v-divider />

        <v-card-text style="max-height: 78vh; overflow-y: auto">
          <v-alert
            v-if="proposalBanner.text"
            :type="proposalBanner.type"
            variant="tonal"
            class="mb-4"
          >
            {{ proposalBanner.text }}
          </v-alert>

          <v-card variant="outlined" class="mb-6 proposal-campaign-card">
            <v-card-text>
              <div class="d-flex align-start flex-wrap ga-3 mb-4">
                <div class="flex-grow-1">
                  <div class="text-caption text-medium-emphasis mb-1">Campaign name</div>
                  <v-text-field
                    v-model="draftProposal.campaignName"
                    variant="outlined"
                    density="comfortable"
                    hide-details
                  />
                </div>
                <v-chip color="info" size="small">{{ draftProposal.objective }}</v-chip>
                <v-chip :color="proposalRiskColor" size="small">{{ proposalRiskLevel }}</v-chip>
              </div>

              <v-row>
                <v-col cols="12" md="3">
                  <v-card variant="tonal" color="success" class="h-100">
                    <v-card-text>
                      <div class="text-caption">Total daily budget</div>
                      <v-text-field
                        v-model.number="draftProposal.suggestedDailyBudget"
                        type="number"
                        prefix="$"
                        variant="outlined"
                        density="compact"
                        hide-details
                        class="mt-2"
                      />
                    </v-card-text>
                  </v-card>
                </v-col>
                <v-col cols="12" md="3">
                  <v-card variant="tonal" color="primary" class="h-100">
                    <v-card-text>
                      <div class="text-caption">Confidence score</div>
                      <div class="d-flex align-center ga-3 mt-3">
                        <v-progress-circular
                          :model-value="proposalConfidence"
                          :color="proposalConfidence >= 75 ? 'success' : proposalConfidence >= 50 ? 'warning' : 'error'"
                          size="54"
                          width="6"
                        >
                          {{ proposalConfidence }}%
                        </v-progress-circular>
                        <div class="text-body-2 text-medium-emphasis">
                          Based on warnings, budget fit and structure completeness.
                        </div>
                      </div>
                    </v-card-text>
                  </v-card>
                </v-col>
                <v-col cols="12" md="6">
                  <v-card variant="tonal" color="deep-purple" class="h-100">
                    <v-card-text>
                      <div class="text-caption mb-2">Estimated results</div>
                      <div class="d-flex flex-wrap ga-2 mb-2">
                        <v-chip v-for="metric in estimatedResultChips" :key="metric" size="small" variant="outlined">
                          {{ metric }}
                        </v-chip>
                      </div>
                      <div class="text-body-2">{{ draftProposal.estimatedResults || 'No estimate returned by AI.' }}</div>
                    </v-card-text>
                  </v-card>
                </v-col>
              </v-row>

              <v-expansion-panels variant="accordion" class="mt-4">
                <v-expansion-panel>
                  <v-expansion-panel-title>
                    AI rationale
                  </v-expansion-panel-title>
                  <v-expansion-panel-text>
                    <v-textarea
                      v-model="draftProposal.rationale"
                      variant="outlined"
                      rows="4"
                      auto-grow
                      hide-details
                    />
                  </v-expansion-panel-text>
                </v-expansion-panel>
              </v-expansion-panels>

              <div v-if="draftProposal.warnings.length" class="mt-4">
                <div class="text-subtitle-2 mb-2">Warnings</div>
                <v-alert
                  v-for="(warning, wi) in draftProposal.warnings"
                  :key="wi"
                  type="warning"
                  variant="tonal"
                  density="compact"
                  class="mb-2"
                >
                  {{ warning }}
                </v-alert>
              </div>
            </v-card-text>
          </v-card>

          <div class="text-subtitle-1 font-weight-medium mb-3">Campaign structure</div>
          <div v-for="(adset, ai) in draftProposal.adsets" :key="adset.adsetId" class="mb-5">
            <v-card variant="outlined" class="proposal-adset-card">
              <v-card-title class="d-flex align-center flex-wrap ga-2">
                <v-icon size="18" color="blue">mdi-folder-outline</v-icon>
                <span>Adset {{ ai + 1 }}</span>
                <v-spacer />
                <v-chip size="x-small" color="grey">{{ adset.optimizationGoal }}</v-chip>
              </v-card-title>
              <v-card-text>
                <v-row>
                  <v-col cols="12" md="5">
                    <div class="text-caption text-medium-emphasis mb-1">Adset name</div>
                    <v-text-field v-model="adset.name" variant="outlined" density="compact" hide-details />
                  </v-col>
                  <v-col cols="12" md="2">
                    <div class="text-caption text-medium-emphasis mb-1">Daily budget</div>
                    <v-text-field v-model.number="adset.dailyBudget" type="number" prefix="$" variant="outlined" density="compact" hide-details />
                  </v-col>
                  <v-col cols="12" md="5">
                    <div class="text-caption text-medium-emphasis mb-1">Optimization goal</div>
                    <v-select
                      v-model="adset.optimizationGoal"
                      :items="['CONVERSIONS', 'LINK_CLICKS', 'IMPRESSIONS', 'REACH']"
                      variant="outlined"
                      density="compact"
                      hide-details
                    />
                  </v-col>
                  <v-col cols="12">
                    <v-card variant="tonal" color="blue-grey-lighten-5">
                      <v-card-text>
                        <div class="text-caption text-medium-emphasis mb-2">Targeting summary</div>
                        <div class="text-body-2 mb-2">{{ targetingSummary(adset.targetingJson) }}</div>
                        <v-textarea
                          v-model="adset.targetingJson"
                          label="Targeting JSON"
                          rows="3"
                          auto-grow
                          variant="outlined"
                          hide-details
                        />
                      </v-card-text>
                    </v-card>
                  </v-col>
                </v-row>

                <div class="proposal-tree-branch mt-4">
                  <v-row>
                    <v-col v-for="(ad, adi) in adset.ads" :key="ad.adId" cols="12" xl="6">
                      <v-card variant="outlined" class="proposal-ad-card h-100">
                        <v-card-title class="d-flex align-center flex-wrap ga-2">
                          <v-icon size="18" color="orange">mdi-bullhorn-outline</v-icon>
                          <span>Ad {{ ai + 1 }}.{{ adi + 1 }}</span>
                        </v-card-title>
                        <v-card-text>
                          <v-row>
                            <v-col cols="12" md="4">
                              <div class="creative-preview-wrap">
                                <img
                                  v-if="ad.creativeAssetId && creativePreviewUrls[ad.creativeAssetId]"
                                  :src="creativePreviewUrls[ad.creativeAssetId]"
                                  class="creative-preview"
                                />
                                <div v-else class="creative-preview-empty">
                                  <v-icon size="44" color="grey">mdi-image-off-outline</v-icon>
                                  <div class="text-caption text-medium-emphasis text-center mt-2">
                                    {{ creativePreviewErrors[ad.creativeAssetId || `missing-${ad.adId}`] || 'Creative not found in library' }}
                                  </div>
                                </div>
                              </div>
                              <div class="d-flex flex-wrap ga-1 mt-2">
                                <v-chip v-if="ad.creativeAssetId" size="x-small" variant="outlined">
                                  {{ shortId(ad.creativeAssetId) }}
                                </v-chip>
                                <v-chip v-else size="x-small" color="warning">No asset</v-chip>
                              </div>
                            </v-col>
                            <v-col cols="12" md="8">
                              <v-text-field v-model="ad.name" label="Ad name" variant="outlined" density="compact" class="mb-2" hide-details />
                              <v-textarea v-model="ad.primaryText" label="Primary text" variant="outlined" rows="3" auto-grow class="mb-2" hide-details />
                              <v-text-field v-model="ad.headline" label="Headline" variant="outlined" density="compact" class="mb-2" hide-details />
                              <v-text-field v-model="ad.description" label="Description" variant="outlined" density="compact" class="mb-2" hide-details />
                              <v-row>
                                <v-col cols="12" md="4">
                                  <v-select
                                    v-model="ad.cta"
                                    :items="ctaOptions"
                                    label="CTA"
                                    variant="outlined"
                                    density="compact"
                                    hide-details
                                  />
                                </v-col>
                                <v-col cols="12" md="8">
                                  <v-text-field v-model="ad.url" label="Destination URL" variant="outlined" density="compact" hide-details />
                                </v-col>
                              </v-row>
                            </v-col>
                          </v-row>
                        </v-card-text>
                      </v-card>
                    </v-col>
                  </v-row>
                </div>
              </v-card-text>
            </v-card>
          </div>
        </v-card-text>

        <v-divider />

        <v-card-actions>
          <v-btn variant="text" :disabled="publishLoading" @click="onRegenerate">
            <v-icon start>mdi-refresh</v-icon> Regenerate
          </v-btn>
          <v-btn variant="outlined" :disabled="publishLoading" @click="onSaveDraft">
            <v-icon start>mdi-content-save-outline</v-icon> Save as Draft
          </v-btn>
          <v-spacer />
          <v-btn :disabled="publishLoading" @click="onCancelProposal">Cancel</v-btn>
          <v-btn
            color="success"
            :loading="publishLoading"
            @click="onApproveAndPublish"
          >
            <v-icon start>mdi-rocket-launch</v-icon> Approve & Publish
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, reactive, watch } from 'vue'
import api from '@/api/client'
import { useCampaignStore } from '@/stores/campaigns'
import { useClientStore } from '@/stores/clients'
import { useCreativeStore } from '@/stores/creatives'
import { useDashboardStore } from '@/stores/dashboard'
import type { CampaignProposal, ProposedAd, ProposedAdset } from '@/stores/campaigns'

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
const publishStep = ref('')
const draftProposal = ref<CampaignProposal | null>(null)
const proposalBanner = ref<{ text: string; type: 'success' | 'error' | 'info' | 'warning' }>({ text: '', type: 'info' })
const creativePreviewUrls = reactive<Record<string, string>>({})
const creativePreviewErrors = reactive<Record<string, string>>({})

const ctaOptions = ['LEARN_MORE', 'SHOP_NOW', 'SIGN_UP', 'GET_OFFER', 'CONTACT_US']

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

function parseTargeting(json: any): Record<string, any> {
  if (!json) return {}
  if (typeof json === 'object') return json
  try {
    return JSON.parse(json)
  } catch {
    return {}
  }
}

function targetingSummary(json: any): string {
  const targeting = parseTargeting(json)
  const parts: string[] = []
  if (targeting.interests?.length) parts.push(`Audiences: ${targeting.interests.join(', ')}`)
  if (targeting.age_min || targeting.age_max) parts.push(`Age: ${targeting.age_min || '?'}-${targeting.age_max || '?'}`)
  if (targeting.genders?.length) parts.push(`Gender: ${targeting.genders.join(', ')}`)
  if (targeting.geo_locations?.countries?.length) parts.push(`Locations: ${targeting.geo_locations.countries.join(', ')}`)
  if (targeting.locations?.length) parts.push(`Locations: ${targeting.locations.join(', ')}`)
  return parts.length ? parts.join(' • ') : 'No targeting summary available.'
}

function shortId(value: string | null | undefined): string {
  return value ? `${value.slice(0, 8)}…` : '—'
}

function cloneProposal(proposal: CampaignProposal): CampaignProposal {
  return JSON.parse(JSON.stringify(proposal))
}

const proposalConfidence = computed(() => {
  if (!draftProposal.value) return 0
  const warningPenalty = Math.min((draftProposal.value.warnings?.length || 0) * 10, 40)
  const missingCreativePenalty = draftProposal.value.adsets.flatMap((adset) => adset.ads).filter((ad) => !ad.creativeAssetId).length * 5
  return Math.max(35, Math.min(96, 90 - warningPenalty - missingCreativePenalty))
})

const proposalRiskLevel = computed(() => {
  const score = proposalConfidence.value
  if (score >= 75) return 'LOW RISK'
  if (score >= 55) return 'MEDIUM RISK'
  return 'HIGH RISK'
})

const proposalRiskColor = computed(() => {
  if (proposalRiskLevel.value === 'LOW RISK') return 'success'
  if (proposalRiskLevel.value === 'MEDIUM RISK') return 'warning'
  return 'error'
})

const estimatedResultChips = computed(() => {
  if (!draftProposal.value?.estimatedResults) return []
  return draftProposal.value.estimatedResults
    .split(/[,;]|\n/)
    .map((item) => item.trim())
    .filter(Boolean)
    .slice(0, 6)
})

watch(() => store.proposal, async (proposal) => {
  if (!proposal) {
    draftProposal.value = null
    return
  }
  draftProposal.value = cloneProposal(proposal)
  await loadCreativePreviews(draftProposal.value)
}, { deep: true })

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
    proposalBanner.value = { text: '', type: 'info' }
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
  await onGenerateProposal()
}

async function onApproveAndPublish() {
  if (!draftProposal.value) return
  publishLoading.value = true
  publishStep.value = 'Creating campaign...'
  proposalBanner.value = { text: 'Creating campaign...', type: 'info' }
  const stepTimers = [
    window.setTimeout(() => {
      publishStep.value = 'Creating adsets...'
      proposalBanner.value = { text: 'Creating adsets...', type: 'info' }
    }, 700),
    window.setTimeout(() => {
      publishStep.value = 'Creating ads...'
      proposalBanner.value = { text: 'Creating ads...', type: 'info' }
    }, 1500),
  ]
  try {
    const result = await store.metaPublish(draftProposal.value.campaignId)
    if (result.status === 'PUBLISHED') {
      proposalBanner.value = { text: 'Campaign published successfully.', type: 'success' }
      showProposalResult.value = false
      // Refresh campaigns list
      if (selectedClient.value) await store.fetchCampaigns(selectedClient.value)
    }
  } catch (e: any) {
    store.error = e.response?.data?.message || e.message
    proposalBanner.value = { text: store.error || 'Publish failed. Campaign remains in DRAFT.', type: 'error' }
  } finally {
    stepTimers.forEach((timer) => window.clearTimeout(timer))
    publishLoading.value = false
    publishStep.value = ''
  }
}

function openAiProposalDialog() {
  if (!selectedClient.value) return
  proposalBanner.value = { text: '', type: 'info' }
  showAiProposal.value = true
}

function onCancelProposal() {
  showProposalResult.value = false
  proposalBanner.value = { text: 'Proposal discarded from preview. The generated campaign remains in DRAFT.', type: 'warning' }
}

async function onSaveDraft() {
  if (!draftProposal.value || !selectedClient.value) return
  showProposalResult.value = false
  await store.fetchCampaigns(selectedClient.value)
  proposalBanner.value = { text: 'Draft saved. You can publish it later from the campaigns list.', type: 'success' }
}

async function loadCreativePreviews(proposal: CampaignProposal | null) {
  if (!proposal) return
  const assetIds = proposal.adsets
    .flatMap((adset: ProposedAdset) => adset.ads)
    .map((ad: ProposedAd) => ad.creativeAssetId)
    .filter((assetId): assetId is string => Boolean(assetId))

  await Promise.all(assetIds.map(async (assetId) => {
    if (creativePreviewUrls[assetId] || creativePreviewErrors[assetId]) return
    try {
      const { data } = await api.get(`/creatives/${assetId}/url`)
      creativePreviewUrls[assetId] = data.url
    } catch {
      creativePreviewErrors[assetId] = 'Creative not found in library'
    }
  }))
}

onMounted(() => clientStore.fetchClients())
</script>

<style scoped>
.proposal-campaign-card {
  border-width: 2px;
}

.proposal-adset-card {
  position: relative;
}

.proposal-adset-card::before {
  content: '';
  position: absolute;
  left: 18px;
  top: 56px;
  bottom: 18px;
  width: 2px;
  background: rgba(63, 81, 181, 0.15);
}

.proposal-tree-branch {
  margin-left: 12px;
  padding-left: 18px;
}

.proposal-ad-card {
  border-style: dashed;
}

.creative-preview-wrap {
  width: 100%;
  height: 180px;
  border-radius: 12px;
  overflow: hidden;
  background: #f5f5f5;
}

.creative-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.creative-preview-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12px;
}
</style>
