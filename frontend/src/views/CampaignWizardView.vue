<template>
  <div>
    <div class="d-flex align-center ga-3 mb-4">
      <v-btn icon variant="text" @click="router.back()"><v-icon>mdi-arrow-left</v-icon></v-btn>
      <h1>Create Campaign</h1>
    </div>

    <v-stepper v-model="step" :items="stepItems" alt-labels>
      <!-- Step 1: Campaign Settings -->
      <template v-slot:[`item.1`]>
        <v-card flat class="pa-4">
          <v-row>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="campaign.name"
                label="Campaign Name"
                :rules="[rules.required]"
                variant="outlined"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-select
                v-model="campaign.objective"
                :items="objectiveOptions"
                item-title="label"
                item-value="value"
                label="Objective"
                :rules="[rules.required]"
                variant="outlined"
              />
            </v-col>
            <v-col cols="12">
              <div class="text-subtitle-2 mb-2">Budget Type</div>
              <v-radio-group v-model="campaign.budgetType" inline>
                <v-radio
                  v-for="option in budgetTypeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </v-radio-group>
            </v-col>
            <v-col v-if="campaign.budgetType === 'CBO'" cols="12" md="6">
              <v-text-field
                v-model.number="campaign.dailyBudget"
                label="Campaign Daily Budget"
                type="number"
                prefix="$"
                variant="outlined"
                :rules="[rules.required, rules.positive]"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-select
                v-model="campaign.specialAdCategories"
                :items="specialAdCategoryOptions"
                label="Special Ad Categories (optional)"
                variant="outlined"
                multiple
                chips
                closable-chips
                clearable
              />
            </v-col>
            <v-col v-if="campaign.budgetType === 'CBO' && adsets.length < 2" cols="12">
              <v-alert type="warning" variant="tonal" density="compact">
                CBO works best with 2+ ad sets.
              </v-alert>
            </v-col>
          </v-row>
        </v-card>
      </template>

      <!-- Step 2: Ad Sets -->
      <template v-slot:[`item.2`]>
        <v-card flat class="pa-4">
          <div class="d-flex justify-space-between align-center mb-4">
            <div class="text-body-1 font-weight-medium">Ad Sets ({{ adsets.length }})</div>
            <v-btn
              color="primary"
              variant="outlined"
              :disabled="adsets.length >= 10"
              @click="addAdset"
            >
              <v-icon start>mdi-plus</v-icon> Add Ad Set
            </v-btn>
          </div>

          <v-alert v-if="campaign.budgetType === 'CBO'" type="info" variant="tonal" class="mb-4">
            Budget is managed at campaign level (${{ Number(campaign.dailyBudget || 0).toFixed(2) }}/day).
          </v-alert>

          <v-expansion-panels v-model="expandedAdset" variant="accordion">
            <v-expansion-panel v-for="(adset, ai) in adsets" :key="ai">
              <v-expansion-panel-title>
                <div class="d-flex align-center ga-2 w-100 pr-4">
                  <v-icon size="18" color="blue">mdi-folder-outline</v-icon>
                  <span class="font-weight-medium">{{ adset.name || `Ad Set ${ai + 1}` }}</span>
                  <v-chip size="x-small" color="success" variant="tonal">
                    {{ campaign.budgetType === 'ABO' ? `$${adset.dailyBudget || 0}/day` : 'Campaign-managed budget' }}
                  </v-chip>
                  <v-chip size="x-small" variant="outlined">
                    {{ adset.ads.length }} ad{{ adset.ads.length !== 1 ? 's' : '' }}
                  </v-chip>
                  <v-spacer />
                  <v-btn
                    v-if="adsets.length > 1"
                    icon
                    size="x-small"
                    color="error"
                    variant="text"
                    @click.stop="removeAdset(ai)"
                  >
                    <v-icon size="16">mdi-delete</v-icon>
                  </v-btn>
                </div>
              </v-expansion-panel-title>
              <v-expansion-panel-text>
                <v-row>
                  <v-col cols="12" md="4">
                    <v-text-field v-model="adset.name" label="Ad Set Name" variant="outlined" :rules="[rules.required]" />
                  </v-col>
                  <v-col v-if="campaign.budgetType === 'ABO'" cols="12" md="2">
                    <v-text-field v-model.number="adset.dailyBudget" label="Daily Budget" type="number" prefix="$" variant="outlined" :rules="[rules.required, rules.positive]" />
                  </v-col>
                  <v-col :cols="12" :md="campaign.budgetType === 'ABO' ? 3 : 4">
                    <v-select v-model="adset.optimizationGoal" :items="optimizationGoalOptions" item-title="label" item-value="value" label="Optimization Goal" variant="outlined" />
                  </v-col>
                  <v-col :cols="12" :md="campaign.budgetType === 'ABO' ? 3 : 4">
                    <v-select v-model="adset.billingEvent" :items="billingEventOptions" label="Billing Event" variant="outlined" />
                  </v-col>
                  <v-col v-if="adset.optimizationGoal === 'OFFSITE_CONVERSIONS'" cols="12" md="4">
                    <v-select
                      v-model="adset.conversionEvent"
                      :items="conversionEventOptions"
                      label="Conversion Event"
                      variant="outlined"
                    />
                  </v-col>
                </v-row>
                <v-alert v-if="campaign.budgetType === 'CBO'" type="info" variant="tonal" density="compact" class="mb-4">
                  Budget is managed at campaign level (${{ Number(campaign.dailyBudget || 0).toFixed(2) }}/day).
                </v-alert>
                <v-row>
                  <v-col cols="12" md="4">
                    <v-text-field v-model="adset.startDate" label="Start Date" type="date" variant="outlined" :rules="[rules.required]" />
                  </v-col>
                  <v-col cols="12" md="4">
                    <v-text-field v-model="adset.endDate" label="End Date (optional)" type="date" variant="outlined" />
                  </v-col>
                </v-row>

                <!-- Targeting -->
                <v-card variant="tonal" color="blue-grey-lighten-5" class="mt-4">
                  <v-card-title class="text-subtitle-2">Targeting</v-card-title>
                  <v-card-text>
                    <v-alert v-if="metaConnected === false" type="info" variant="tonal" density="compact" class="mb-4">
                      Meta is not connected for this client. You can still type targeting values manually — connect Meta in Client Settings to enable live search.
                    </v-alert>

                    <LocationSearchField
                      v-model="adset.targeting.locations"
                      :client-id="clientId"
                      label="Target Locations"
                      hint="Search for countries, cities, or regions"
                      required
                    />

                    <div class="mt-4">
                      <div class="text-caption font-weight-medium mb-2">Age Range</div>
                      <v-range-slider
                        v-model="adset.targeting.ageRange"
                        :min="18"
                        :max="65"
                        :step="1"
                        thumb-label="always"
                        strict
                        color="primary"
                      />
                    </div>

                    <div class="mt-2">
                      <div class="text-caption font-weight-medium mb-2">Gender</div>
                      <v-btn-toggle v-model="adset.targeting.gender" mandatory color="primary">
                        <v-btn value="all">All</v-btn>
                        <v-btn value="male">Male</v-btn>
                        <v-btn value="female">Female</v-btn>
                      </v-btn-toggle>
                    </div>

                    <InterestSearchField
                      v-model="adset.targeting.interests"
                      :client-id="clientId"
                      label="Interests"
                      hint="Search for interests, behaviors, demographics"
                      class="mt-4"
                    />

                    <AudienceSelector
                      v-model="adset.targeting.customAudiences"
                      :client-id="clientId"
                      label="Custom Audiences (Include)"
                      hint="Select audiences to include"
                      class="mt-4"
                    />

                    <AudienceSelector
                      v-model="adset.targeting.excludedAudiences"
                      :client-id="clientId"
                      label="Excluded Audiences"
                      hint="Select audiences to exclude"
                      class="mt-4"
                    />

                    <!-- Placements -->
                    <div class="text-caption font-weight-medium mb-2 mt-3">Placements</div>
                    <v-radio-group v-model="adset.placements" inline>
                      <v-radio label="Automatic (recommended)" value="automatic" />
                      <v-radio label="Manual" value="manual" />
                    </v-radio-group>
                    <div v-if="adset.placements === 'manual'" class="d-flex flex-wrap ga-2 mb-3">
                      <v-checkbox
                        v-for="p in placementOptions"
                        :key="p.value"
                        v-model="adset.manualPlacements"
                        :label="p.label"
                        :value="p.value"
                        density="compact"
                        hide-details
                      />
                    </div>
                  </v-card-text>
                </v-card>
              </v-expansion-panel-text>
            </v-expansion-panel>
          </v-expansion-panels>
        </v-card>
      </template>

      <!-- Step 3: Ads -->
      <template v-slot:[`item.3`]>
        <v-card flat class="pa-4">
          <div v-for="(adset, ai) in adsets" :key="ai" class="mb-6">
            <div class="text-subtitle-1 font-weight-medium d-flex align-center ga-2 mb-3">
              <v-icon size="18" color="blue">mdi-folder-outline</v-icon>
              {{ adset.name || `Ad Set ${ai + 1}` }}
              <v-chip size="x-small" color="grey" variant="outlined">{{ adset.ads.length }} ad{{ adset.ads.length !== 1 ? 's' : '' }}</v-chip>
            </div>

            <div v-for="(ad, adi) in adset.ads" :key="adi" class="mb-4">
              <v-card variant="outlined">
                <v-card-title class="d-flex align-center ga-2">
                  <v-icon size="18" color="orange">mdi-bullhorn-outline</v-icon>
                  <span>Ad {{ ai + 1 }}.{{ adi + 1 }}</span>
                  <v-spacer />
                  <v-btn v-if="adset.ads.length > 1" icon size="x-small" color="error" variant="text" @click="removeAd(ai, adi)">
                    <v-icon size="16">mdi-delete</v-icon>
                  </v-btn>
                </v-card-title>
                <v-card-text>
                  <v-row>
                    <v-col cols="12" md="6">
                      <v-text-field v-model="ad.name" label="Ad Name" variant="outlined" :rules="[rules.required]" />
                    </v-col>
                    <v-col cols="12" md="6">
                      <v-text-field v-model="ad.destinationUrl" label="Destination URL" variant="outlined" placeholder="https://example.com" :rules="[rules.required]" />
                    </v-col>
                  </v-row>

                  <v-row>
                    <v-col cols="12" md="4">
                      <div class="text-caption font-weight-medium mb-2">Creative</div>
                      <div v-if="ad.creativeAssetId" class="d-flex align-center ga-3">
                        <div class="selected-creative-thumb">
                          <img v-if="ad.creativeThumbnailUrl" :src="ad.creativeThumbnailUrl" class="selected-creative-thumb-img" />
                          <v-icon v-else size="32" color="grey">mdi-image</v-icon>
                        </div>
                        <div class="flex-grow-1">
                          <div class="text-body-2 text-truncate">{{ ad.creativeFilename }}</div>
                          <v-btn size="small" variant="text" color="primary" @click="openCreativeSelector(ai, adi)">Change</v-btn>
                        </div>
                      </div>
                      <v-btn v-else variant="outlined" @click="openCreativeSelector(ai, adi)">
                        <v-icon start>mdi-image-plus</v-icon> Select Creative
                      </v-btn>
                    </v-col>

                    <v-col cols="12" md="8">
                      <div class="text-caption font-weight-medium mb-2">Ad Copy</div>

                      <v-btn-toggle v-model="ad.copyMode" mandatory class="mb-3">
                        <v-btn value="custom" size="small">Write Custom</v-btn>
                        <v-btn
                          value="variant"
                          size="small"
                          :disabled="!ad.creativeAssetId || !getCopyVariants(ad.creativeAssetId).length"
                        >
                          From Copy Variants ({{ ad.creativeAssetId ? getCopyVariants(ad.creativeAssetId).length : 0 }})
                        </v-btn>
                      </v-btn-toggle>

                      <template v-if="ad.copyMode === 'variant' && ad.creativeAssetId">
                        <v-select
                          v-model="ad.selectedCopyVariantId"
                          :items="getCopyVariants(ad.creativeAssetId)"
                          item-title="display"
                          item-value="id"
                          label="Select Copy Variant"
                          variant="outlined"
                          density="compact"
                          @update:model-value="(val: string) => applyCopyVariant(ai, adi, val)"
                        />
                      </template>

                      <v-row>
                        <v-col cols="12">
                          <v-textarea v-model="ad.primaryText" label="Primary Text" variant="outlined" rows="3" auto-grow counter="125" />
                        </v-col>
                        <v-col cols="12" md="6">
                          <v-text-field v-model="ad.headline" label="Headline" variant="outlined" counter="40" />
                        </v-col>
                        <v-col cols="12" md="6">
                          <v-text-field v-model="ad.description" label="Description" variant="outlined" counter="30" />
                        </v-col>
                        <v-col cols="12" md="4">
                          <v-select v-model="ad.ctaType" :items="ctaOptions" label="CTA" variant="outlined" />
                        </v-col>
                      </v-row>

                      <v-btn
                        v-if="ad.creativeAssetId"
                        variant="tonal"
                        color="deep-purple"
                        size="small"
                        class="mt-2"
                        :loading="generatingCopy[`${ai}-${adi}`]"
                        @click="generateCopyForAd(ai, adi)"
                      >
                        <v-icon start>mdi-auto-fix</v-icon> Generate Copy with AI
                      </v-btn>
                    </v-col>
                  </v-row>
                </v-card-text>
              </v-card>
            </div>

            <div class="d-flex ga-2 flex-wrap">
              <v-btn
                variant="outlined"
                color="primary"
                size="small"
                :disabled="adset.ads.length >= 10"
                @click="addAd(ai)"
              >
                <v-icon start>mdi-plus</v-icon> Add Another Ad
              </v-btn>
              <v-btn
                variant="tonal"
                color="deep-purple"
                size="small"
                @click="openPackageImport(ai)"
              >
                <v-icon start>mdi-package-variant</v-icon> Import from Package
              </v-btn>
            </div>

            <v-divider v-if="ai < adsets.length - 1" class="mt-6" />
          </div>
        </v-card>
      </template>

      <!-- Step 4: Review & Publish -->
      <template v-slot:[`item.4`]>
        <v-card flat class="pa-4">
          <v-alert v-if="validationErrors.length" type="error" variant="tonal" class="mb-4">
            <div class="font-weight-medium mb-2">Please fix the following issues:</div>
            <ul>
              <li v-for="(err, i) in validationErrors" :key="i">{{ err }}</li>
            </ul>
          </v-alert>

          <v-row class="mb-4">
            <v-col cols="12" md="3">
              <v-card variant="tonal" color="primary">
                <v-card-text>
                  <div class="text-caption">Campaign</div>
                  <div class="text-h6">{{ campaign.name || 'Untitled' }}</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="3">
              <v-card variant="tonal" color="secondary">
                <v-card-text>
                  <div class="text-caption">Objective</div>
                  <div class="text-h6">{{ objectiveLabel(campaign.objective) }}</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="3">
              <v-card variant="tonal" color="success">
                <v-card-text>
                  <div class="text-caption">{{ campaign.budgetType === 'CBO' ? 'Campaign Daily Budget' : 'Total Daily Budget' }}</div>
                  <div class="text-h6">${{ totalDailyBudget.toFixed(2) }}</div>
                </v-card-text>
              </v-card>
            </v-col>
            <v-col cols="12" md="3">
              <v-card variant="tonal" color="info">
                <v-card-text>
                  <div class="text-caption">Structure</div>
                  <div class="text-h6">{{ adsets.length }} adset{{ adsets.length !== 1 ? 's' : '' }}, {{ totalAds }} ad{{ totalAds !== 1 ? 's' : '' }}</div>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>

          <div v-for="(adset, ai) in adsets" :key="ai" class="mb-5">
            <v-card variant="outlined">
              <v-card-title class="d-flex align-center flex-wrap ga-2">
                <v-icon size="18" color="blue">mdi-folder-outline</v-icon>
                <span>{{ adset.name || `Ad Set ${ai + 1}` }}</span>
                <v-chip size="x-small" color="success" variant="tonal">
                  {{ campaign.budgetType === 'ABO' ? `$${adset.dailyBudget || 0}/day` : 'Campaign-managed budget' }}
                </v-chip>
                <v-chip size="x-small" variant="outlined">{{ adset.optimizationGoal }}</v-chip>
              </v-card-title>
              <v-card-text>
                <div class="text-body-2 text-medium-emphasis mb-3">{{ targetingSummary(adset) }}</div>
                <v-row>
                  <v-col v-for="(ad, adi) in adset.ads" :key="adi" cols="12" md="6" xl="4">
                    <AdPreviewCard
                      :image-url="ad.creativeThumbnailUrl"
                      :headline="ad.headline"
                      :primary-text="ad.primaryText"
                      :description="ad.description"
                      :cta="ad.ctaType"
                      :destination-url="ad.destinationUrl"
                    />
                  </v-col>
                </v-row>
              </v-card-text>
            </v-card>
          </div>
        </v-card>
      </template>

      <template #actions>
        <div class="d-flex w-100 pa-4 ga-3 justify-space-between flex-wrap">
          <v-btn v-if="step > 1" variant="outlined" @click="step -= 1">
            <v-icon start>mdi-arrow-left</v-icon> Back
          </v-btn>
          <v-spacer />
          <template v-if="step < 4">
            <v-btn color="primary" :disabled="!canAdvance" @click="step += 1">
              Next <v-icon end>mdi-arrow-right</v-icon>
            </v-btn>
          </template>
          <template v-else>
            <v-btn variant="outlined" :loading="saving" @click="saveDraft">
              <v-icon start>mdi-content-save-outline</v-icon> Save as Draft
            </v-btn>
            <v-btn color="success" :loading="publishing" :disabled="validationErrors.length > 0" @click="saveAndPublish">
              <v-icon start>mdi-rocket-launch</v-icon> Save & Publish to Meta
            </v-btn>
          </template>
        </div>
      </template>
    </v-stepper>

    <CreativeSelectorDialog
      v-model="showCreativeSelector"
      :client-id="clientId"
      @selected="onCreativeSelected"
    />

    <!-- Package Import Dialog -->
    <v-dialog v-model="showPackageImport" max-width="900" scrollable>
      <v-card>
        <v-card-title class="d-flex align-center ga-2">
          <v-icon>mdi-package-variant</v-icon>
          <span>Import from Creative Package</span>
          <v-spacer />
          <v-btn icon size="small" @click="showPackageImport = false"><v-icon>mdi-close</v-icon></v-btn>
        </v-card-title>
        <v-divider />

        <v-card-text style="min-height: 350px">
          <v-progress-linear v-if="loadingPackages" indeterminate color="deep-purple" class="mb-4" />

          <v-alert v-if="!loadingPackages && approvedPackages.length === 0" type="info" variant="tonal">
            No approved creative packages found. Create and approve packages in the Creatives section first.
          </v-alert>

          <template v-else>
            <div class="text-body-2 text-medium-emphasis mb-4">
              Select a package to import all its creative + copy combinations as ads into
              <strong>{{ adsets[packageImportAdsetIdx]?.name || `Ad Set ${packageImportAdsetIdx + 1}` }}</strong>.
            </div>

            <v-row>
              <v-col v-for="pkg in approvedPackages" :key="pkg.id" cols="12" md="6">
                <v-card variant="outlined" class="package-import-card" @click="importPackage(pkg)">
                  <v-card-title class="text-body-1">
                    <v-icon start size="18" color="deep-purple">mdi-package-variant</v-icon>
                    {{ pkg.name }}
                  </v-card-title>
                  <v-card-text>
                    <div class="d-flex ga-2 mb-2">
                      <v-chip size="x-small" color="success" variant="tonal">{{ pkg.status }}</v-chip>
                      <v-chip v-if="pkg.objective" size="x-small" variant="outlined">{{ pkg.objective }}</v-chip>
                      <v-chip size="x-small" variant="outlined">{{ pkg.itemCount }} item{{ pkg.itemCount !== 1 ? 's' : '' }}</v-chip>
                    </div>
                    <div v-for="item in pkg.items.slice(0, 4)" :key="item.id" class="d-flex align-center ga-2 mb-1">
                      <v-icon size="14" :color="item.creativeAsset ? 'green' : 'grey'">
                        {{ item.creativeAsset?.assetType === 'VIDEO' ? 'mdi-video' : 'mdi-image' }}
                      </v-icon>
                      <span class="text-caption text-truncate" style="max-width: 180px">
                        {{ item.creativeAsset?.originalFilename || 'No creative' }}
                      </span>
                      <span v-if="item.copyVariant" class="text-caption text-medium-emphasis text-truncate" style="max-width: 140px">
                        — {{ item.copyVariant.headline || 'No headline' }}
                      </span>
                    </div>
                    <div v-if="pkg.items.length > 4" class="text-caption text-medium-emphasis mt-1">
                      + {{ pkg.items.length - 4 }} more item{{ pkg.items.length - 4 !== 1 ? 's' : '' }}
                    </div>
                  </v-card-text>
                </v-card>
              </v-col>
            </v-row>
          </template>
        </v-card-text>

        <v-divider />
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showPackageImport = false">Cancel</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Publish Progress Dialog -->
    <v-dialog v-model="publishDialog.show" persistent max-width="550">
      <v-card>
        <v-card-title class="d-flex align-center ga-2">
          <v-icon :color="publishDialog.error ? 'error' : publishDialog.done ? 'success' : 'primary'">
            {{ publishDialog.error ? 'mdi-alert-circle' : publishDialog.done ? 'mdi-check-circle' : 'mdi-rocket-launch' }}
          </v-icon>
          <span>{{ publishDialog.error ? 'Publish Failed' : publishDialog.done ? 'Published!' : 'Publishing to Meta...' }}</span>
        </v-card-title>
        <v-card-text>
          <v-progress-linear v-if="!publishDialog.done && !publishDialog.error" indeterminate color="primary" class="mb-4" />

          <div v-for="(s, i) in publishDialog.steps" :key="i" class="d-flex align-center ga-2 mb-2">
            <v-icon size="18" :color="s.status === 'done' ? 'success' : s.status === 'warning' ? 'warning' : s.status === 'error' ? 'error' : 'grey'">
              {{ s.status === 'done' ? 'mdi-check-circle' : s.status === 'warning' ? 'mdi-alert' : s.status === 'error' ? 'mdi-close-circle' : 'mdi-circle-outline' }}
            </v-icon>
            <span class="text-body-2">{{ s.message }}</span>
          </div>

          <v-alert v-if="publishDialog.error" type="error" variant="tonal" class="mt-4">
            {{ publishDialog.error }}
          </v-alert>

          <v-alert v-if="publishDialog.done" type="success" variant="tonal" class="mt-4">
            Campaign is now live on Meta! Redirecting to campaigns list...
          </v-alert>
        </v-card-text>
        <v-card-actions v-if="publishDialog.error">
          <v-spacer />
          <v-btn @click="publishDialog.show = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="4000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/client'
import AdPreviewCard from '@/components/AdPreviewCard.vue'
import AudienceSelector from '@/components/AudienceSelector.vue'
import CreativeSelectorDialog from '@/components/CreativeSelectorDialog.vue'
import InterestSearchField from '@/components/InterestSearchField.vue'
import LocationSearchField from '@/components/LocationSearchField.vue'
import { useCreativeStore } from '@/stores/creatives'
import type { CopyVariant, PackageDetail } from '@/stores/creatives'
import type { MetaAudienceOption, MetaInterestOption, MetaLocationOption } from '@/types/metaTargeting'

const route = useRoute()
const router = useRouter()
const creativeStore = useCreativeStore()

const clientId = computed(() => route.params.clientId as string)
const step = ref(1)
const saving = ref(false)
const publishing = ref(false)
const showCreativeSelector = ref(false)
const pendingCreativeSlot = ref<{ adsetIdx: number; adIdx: number } | null>(null)
const generatingCopy = reactive<Record<string, boolean>>({})
const snackbar = ref({ show: false, text: '', color: 'success' as string })
const metaConnected = ref<boolean | null>(null)
const metaConnectionMessage = ref('')

// Publish progress dialog
const publishDialog = reactive({
  show: false,
  done: false,
  error: '' as string,
  steps: [] as { step: string; status: string; message: string }[],
})

// Copy variants cache
const copyVariantsCache = reactive<Record<string, CopyVariant[]>>({})

// Package import state
const showPackageImport = ref(false)
const packageImportAdsetIdx = ref<number>(0)
const approvedPackages = ref<PackageDetail[]>([])
const loadingPackages = ref(false)
const stepItems = [
  { title: 'Campaign Settings', value: 1 },
  { title: 'Ad Sets', value: 2 },
  { title: 'Ads', value: 3 },
  { title: 'Review & Publish', value: 4 },
]

const objectiveOptions = [
  { value: 'OUTCOME_SALES', label: 'Sales (Conversions)' },
  { value: 'OUTCOME_LEADS', label: 'Lead Generation' },
  { value: 'OUTCOME_TRAFFIC', label: 'Website Traffic' },
  { value: 'OUTCOME_AWARENESS', label: 'Brand Awareness' },
  { value: 'OUTCOME_ENGAGEMENT', label: 'Engagement' },
  { value: 'OUTCOME_APP_PROMOTION', label: 'App Promotion' },
]

const budgetTypeOptions = [
  { value: 'ABO', label: 'Ad Set Budget (ABO) — each ad set has its own budget' },
  { value: 'CBO', label: 'Campaign Budget (CBO) — one budget, Meta optimizes distribution' },
]

const specialAdCategoryOptions = ['CREDIT', 'EMPLOYMENT', 'HOUSING', 'SOCIAL_ISSUES']

const optimizationGoalOptions = [
  { value: 'OFFSITE_CONVERSIONS', label: 'Conversions' },
  { value: 'LANDING_PAGE_VIEWS', label: 'Landing Page Views' },
  { value: 'LINK_CLICKS', label: 'Link Clicks' },
  { value: 'IMPRESSIONS', label: 'Impressions' },
  { value: 'REACH', label: 'Reach' },
]

const billingEventOptions = ['IMPRESSIONS', 'LINK_CLICKS']

const conversionEventOptions = ['PURCHASE', 'ADD_TO_CART', 'INITIATE_CHECKOUT', 'LEAD', 'COMPLETE_REGISTRATION', 'CONTACT', 'SUBSCRIBE']

const ctaOptions = ['SHOP_NOW', 'LEARN_MORE', 'SIGN_UP', 'BOOK_NOW', 'CONTACT_US', 'GET_OFFER', 'SUBSCRIBE', 'DOWNLOAD']

const placementOptions = [
  { value: 'facebook_feed', label: 'Facebook Feed' },
  { value: 'instagram_feed', label: 'Instagram Feed' },
  { value: 'stories', label: 'Stories' },
  { value: 'reels', label: 'Reels' },
  { value: 'audience_network', label: 'Audience Network' },
  { value: 'messenger', label: 'Messenger' },
]

const rules = {
  required: (v: unknown) => !!v || 'Required',
  positive: (v: number | null | undefined) => (v != null && v > 0) || 'Must be positive',
}

interface ApiErrorShape {
  response?: {
    data?: {
      message?: string
      error?: string
      steps?: { step: string; status: string; message: string }[]
    }
  }
  message?: string
}

function getApiErrorMessage(error: unknown, fallback: string) {
  const apiError = error as ApiErrorShape
  return apiError.response?.data?.message || apiError.response?.data?.error || apiError.message || fallback
}

interface WizardAd {
  name: string
  creativeAssetId: string | null
  creativeFilename: string
  creativeThumbnailUrl: string | null
  copyVariantId: string | null
  selectedCopyVariantId: string | null
  copyMode: 'custom' | 'variant'
  primaryText: string
  headline: string
  description: string
  ctaType: string
  destinationUrl: string
}

interface WizardTargeting {
  ageRange: [number, number]
  gender: 'all' | 'male' | 'female'
  locations: MetaLocationOption[]
  interests: MetaInterestOption[]
  customAudiences: MetaAudienceOption[]
  excludedAudiences: MetaAudienceOption[]
}

interface WizardAdset {
  name: string
  dailyBudget: number
  optimizationGoal: string
  conversionEvent: string
  billingEvent: string
  startDate: string
  endDate: string
  targeting: WizardTargeting
  placements: 'automatic' | 'manual'
  manualPlacements: string[]
  ads: WizardAd[]
}

const campaign = reactive({
  name: '',
  objective: 'OUTCOME_SALES',
  budgetType: 'ABO',
  dailyBudget: null as number | null,
  specialAdCategories: [] as string[],
})

const expandedAdset = ref<number | undefined>(0)

function createDefaultAd(): WizardAd {
  return {
    name: '',
    creativeAssetId: null,
    creativeFilename: '',
    creativeThumbnailUrl: null,
    copyVariantId: null,
    selectedCopyVariantId: null,
    copyMode: 'custom',
    primaryText: '',
    headline: '',
    description: '',
    ctaType: 'LEARN_MORE',
    destinationUrl: '',
  }
}

function createDefaultAdset(): WizardAdset {
  return {
    name: '',
    dailyBudget: 50,
    optimizationGoal: 'OFFSITE_CONVERSIONS',
    conversionEvent: 'PURCHASE',
    billingEvent: 'IMPRESSIONS',
    startDate: '',
    endDate: '',
    targeting: {
      ageRange: [18, 65],
      gender: 'all',
      locations: [],
      interests: [],
      customAudiences: [],
      excludedAudiences: [],
    },
    placements: 'automatic',
    manualPlacements: [],
    ads: [createDefaultAd()],
  }
}

const adsets = ref<WizardAdset[]>([createDefaultAdset()])

const totalDailyBudget = computed(() => campaign.budgetType === 'CBO'
  ? Number(campaign.dailyBudget || 0)
  : adsets.value.reduce((sum, a) => sum + (a.dailyBudget || 0), 0))
const totalAds = computed(() => adsets.value.reduce((sum, a) => sum + a.ads.length, 0))

const canAdvance = computed(() => {
  if (step.value === 1) {
    return !!campaign.name.trim()
      && !!campaign.objective
      && (campaign.budgetType !== 'CBO' || (!!campaign.dailyBudget && campaign.dailyBudget > 0))
  }
  if (step.value === 2) {
    return adsets.value.every((a) => !!a.name.trim() && (campaign.budgetType === 'CBO' || a.dailyBudget > 0))
  }
  if (step.value === 3) return adsets.value.every((a) => a.ads.every((ad) => !!ad.name.trim() && !!ad.destinationUrl.trim()))
  return true
})

const validationErrors = computed(() => {
  const errs: string[] = []
  if (!campaign.name.trim()) errs.push('Campaign name is required')
  if (!campaign.objective) errs.push('Objective is required')
  if (campaign.budgetType === 'CBO' && (!campaign.dailyBudget || campaign.dailyBudget <= 0)) {
    errs.push('Campaign daily budget is required for CBO')
  }
  for (let i = 0; i < adsets.value.length; i++) {
    const a = adsets.value[i]!
    if (!a.name.trim()) errs.push(`Ad Set ${i + 1}: name is required`)
    if (campaign.budgetType === 'ABO' && (!a.dailyBudget || a.dailyBudget <= 0)) errs.push(`Ad Set ${i + 1}: daily budget must be positive`)
    if (!a.startDate) errs.push(`Ad Set ${i + 1}: start date is required`)
    if (!a.targeting.locations.length) errs.push(`Ad Set ${i + 1}: at least one target location is required`)
    for (let j = 0; j < a.ads.length; j++) {
      const ad = a.ads[j]!
      if (!ad.name.trim()) errs.push(`Ad ${i + 1}.${j + 1}: name is required`)
      if (!ad.destinationUrl.trim()) errs.push(`Ad ${i + 1}.${j + 1}: destination URL is required`)
    }
  }
  return errs
})

function objectiveLabel(value: string) {
  return objectiveOptions.find((o) => o.value === value)?.label || value
}

function addAdset() {
  if (adsets.value.length >= 10) return
  adsets.value.push(createDefaultAdset())
  expandedAdset.value = adsets.value.length - 1
}

function removeAdset(idx: number) {
  if (adsets.value.length <= 1) return
  adsets.value.splice(idx, 1)
}

function addAd(adsetIdx: number) {
  const adset = adsets.value[adsetIdx]
  if (!adset || adset.ads.length >= 10) return
  adset.ads.push(createDefaultAd())
}

function removeAd(adsetIdx: number, adIdx: number) {
  const adset = adsets.value[adsetIdx]
  if (!adset || adset.ads.length <= 1) return
  adset.ads.splice(adIdx, 1)
}

function openCreativeSelector(adsetIdx: number, adIdx: number) {
  pendingCreativeSlot.value = { adsetIdx, adIdx }
  showCreativeSelector.value = true
}

async function onCreativeSelected(asset: { id: string; filename: string; thumbnailUrl: string | null; assetType: string }) {
  if (!pendingCreativeSlot.value) return
  const { adsetIdx, adIdx } = pendingCreativeSlot.value
  const ad = adsets.value[adsetIdx]?.ads[adIdx]
  if (!ad) return
  ad.creativeAssetId = asset.id
  ad.creativeFilename = asset.filename
  ad.creativeThumbnailUrl = asset.thumbnailUrl

  // Load copy variants for this creative
  if (!copyVariantsCache[asset.id]) {
    try {
      const variants = await creativeStore.fetchCopyVariants(asset.id, true)
      copyVariantsCache[asset.id] = variants
    } catch {
      copyVariantsCache[asset.id] = []
    }
  }

  pendingCreativeSlot.value = null
}

function getCopyVariants(assetId: string): (CopyVariant & { display: string })[] {
  const variants = copyVariantsCache[assetId] || creativeStore.copyVariantsByAsset[assetId] || []
  return variants
    .filter((v: CopyVariant) => v.status === 'APPROVED' || v.status === 'DRAFT')
    .map((v: CopyVariant) => ({
      ...v,
      display: `${v.headline || 'Untitled'} — ${v.cta || 'N/A'}`,
    }))
}

function applyCopyVariant(adsetIdx: number, adIdx: number, variantId: string) {
  const ad = adsets.value[adsetIdx]?.ads[adIdx]
  if (!ad || !ad.creativeAssetId) return
  const variants = getCopyVariants(ad.creativeAssetId)
  const variant = variants.find((v) => v.id === variantId)
  if (variant) {
    ad.primaryText = variant.primaryText || ''
    ad.headline = variant.headline || ''
    ad.description = variant.description || ''
    ad.ctaType = variant.cta || 'LEARN_MORE'
    ad.copyVariantId = variant.id
  }
}

async function generateCopyForAd(adsetIdx: number, adIdx: number) {
  const ad = adsets.value[adsetIdx]?.ads[adIdx]
  if (!ad || !ad.creativeAssetId) return

  const key = `${adsetIdx}-${adIdx}`
  generatingCopy[key] = true
  try {
    // Ensure analysis exists
    await creativeStore.analyzeAsset(ad.creativeAssetId)
    // Generate copy
    const variants = await creativeStore.generateCopy(ad.creativeAssetId)
    copyVariantsCache[ad.creativeAssetId] = variants

    // Auto-apply first variant
    if (variants.length > 0) {
      const first = variants[0]
      ad.primaryText = first.primaryText || ''
      ad.headline = first.headline || ''
      ad.description = first.description || ''
      ad.ctaType = first.cta || 'LEARN_MORE'
      ad.copyVariantId = first.id
      ad.copyMode = 'variant'
      ad.selectedCopyVariantId = first.id
      snackbar.value = { show: true, text: `Generated ${variants.length} copy variants`, color: 'success' }
    }
  } catch (error: unknown) {
    snackbar.value = { show: true, text: getApiErrorMessage(error, 'Copy generation failed'), color: 'error' }
  } finally {
    generatingCopy[key] = false
  }
}

// ── Package import ──

async function openPackageImport(adsetIdx: number) {
  packageImportAdsetIdx.value = adsetIdx
  showPackageImport.value = true
  loadingPackages.value = true
  try {
    approvedPackages.value = await creativeStore.fetchApprovedPackages(clientId.value)
  } catch {
    approvedPackages.value = []
    snackbar.value = { show: true, text: 'Failed to load packages', color: 'error' }
  } finally {
    loadingPackages.value = false
  }
}

function importPackage(pkg: PackageDetail) {
  const adset = adsets.value[packageImportAdsetIdx.value]
  if (!adset) return

  let imported = 0
  for (const item of pkg.items) {
    if (adset.ads.length >= 10) break

    const ad = createDefaultAd()
    ad.name = `${pkg.name} — ${item.copyVariant?.headline || item.creativeAsset?.originalFilename || `Item ${imported + 1}`}`

    if (item.creativeAsset) {
      ad.creativeAssetId = item.creativeAsset.id
      ad.creativeFilename = item.creativeAsset.originalFilename
      ad.creativeThumbnailUrl = item.creativeAsset.thumbnailUrl || null
    }

    if (item.copyVariant) {
      ad.primaryText = item.copyVariant.primaryText || ''
      ad.headline = item.copyVariant.headline || ''
      ad.description = item.copyVariant.description || ''
      ad.ctaType = item.copyVariant.ctaType || 'LEARN_MORE'
      ad.copyVariantId = item.copyVariant.id
      ad.copyMode = 'variant'
      ad.selectedCopyVariantId = item.copyVariant.id
    }

    // Remove the placeholder empty ad if it's the only one and untouched
    if (
      adset.ads.length === 1 &&
      imported === 0 &&
      !adset.ads[0]!.creativeAssetId &&
      !adset.ads[0]!.name.trim()
    ) {
      adset.ads.splice(0, 1)
    }

    adset.ads.push(ad)
    imported++
  }

  showPackageImport.value = false
  snackbar.value = {
    show: true,
    text: `Imported ${imported} ad${imported !== 1 ? 's' : ''} from "${pkg.name}"`,
    color: 'success',
  }
}

// ── Targeting summary for review ──

function targetingSummary(adset: WizardAdset): string {
  const parts: string[] = []
  const t = adset.targeting
  if (t.locations.length) parts.push(`Locations: ${t.locations.map((l) => l.name).join(', ')}`)
  parts.push(`Age: ${t.ageRange[0]}–${t.ageRange[1]}`)
  if (t.gender !== 'all') parts.push(`Gender: ${t.gender}`)
  if (t.interests.length) parts.push(`Interests: ${t.interests.map((i) => i.name).join(', ')}`)
  if (t.customAudiences.length) parts.push(`Custom audiences: ${t.customAudiences.length}`)
  parts.push(`Placements: ${adset.placements}`)
  if (adset.startDate) parts.push(`Start: ${adset.startDate}`)
  return parts.join(' · ')
}

// ── Build targeting JSON for API ──

function buildTargetingJson(adset: WizardAdset): Record<string, unknown> {
  const t = adset.targeting
  const targeting: Record<string, unknown> = {
    locations: t.locations.map((location) => ({
      key: location.key,
      name: location.name,
      type: location.type,
      country_name: location.country_name,
      country_code: location.country_code,
    })),
    ageRange: t.ageRange,
    gender: t.gender,
    interests: t.interests.map((interest) => ({ id: interest.id, name: interest.name })),
    customAudiences: t.customAudiences.map((audience) => ({ id: audience.id, name: audience.name })),
    excludedAudiences: t.excludedAudiences.map((audience) => ({ id: audience.id, name: audience.name })),
  }

  return targeting
}

// ── Save ──

async function saveDraft() {
  saving.value = true
  try {
    const payload = buildPayload()
    const { data } = await api.post(`/clients/${clientId.value}/campaigns/create`, payload)
    snackbar.value = { show: true, text: 'Campaign saved as draft!', color: 'success' }
    router.push({ name: 'campaigns', query: { clientId: clientId.value } })
    return data
  } catch (error: unknown) {
    snackbar.value = { show: true, text: getApiErrorMessage(error, 'Failed to save'), color: 'error' }
  } finally {
    saving.value = false
  }
}

async function saveAndPublish() {
  if (validationErrors.value.length > 0) return
  publishing.value = true

  // Reset and show progress dialog
  publishDialog.show = true
  publishDialog.done = false
  publishDialog.error = ''
  publishDialog.steps = [{ step: 'save', status: 'done', message: 'Saving campaign draft...' }]

  try {
    const payload = buildPayload()
    const { data } = await api.post(`/clients/${clientId.value}/campaigns/create`, payload)
    publishDialog.steps[0] = { step: 'save', status: 'done', message: 'Campaign saved ✓' }
    publishDialog.steps.push({ step: 'publish', status: 'pending', message: 'Publishing to Meta...' })

    // Now publish — this may take a while
    const { data: result } = await api.post(`/campaigns/${data.campaignId}/meta-publish`)

    // Replace the generic "publishing" step with actual steps from backend
    publishDialog.steps.pop() // remove the "Publishing to Meta..." placeholder
    if (result.steps && Array.isArray(result.steps)) {
      for (const s of result.steps) {
        publishDialog.steps.push(s)
      }
    }

    if (result.status === 'PUBLISHED') {
      publishDialog.done = true
      setTimeout(() => {
        publishDialog.show = false
        router.push({ name: 'campaigns', query: { clientId: clientId.value } })
      }, 2000)
    } else {
      publishDialog.error = result.error || 'Unknown error during publish'
    }
  } catch (error: unknown) {
    const errorData = (error as ApiErrorShape).response?.data
    // If the backend returned steps, show them
    if (errorData?.steps && Array.isArray(errorData.steps)) {
      publishDialog.steps = [
        { step: 'save', status: 'done', message: 'Campaign saved ✓' },
        ...errorData.steps,
      ]
    }
    publishDialog.error = getApiErrorMessage(error, 'Failed to publish')
  } finally {
    publishing.value = false
  }
}

function buildPayload() {
  return {
    name: campaign.name,
    objective: campaign.objective,
    budgetType: campaign.budgetType,
    dailyBudget: campaign.budgetType === 'CBO' ? campaign.dailyBudget : null,
    specialAdCategories: campaign.specialAdCategories,
    adsets: adsets.value.map((adset) => ({
      name: adset.name,
      dailyBudget: campaign.budgetType === 'ABO' ? adset.dailyBudget : null,
      optimizationGoal: adset.optimizationGoal,
      conversionEvent: adset.optimizationGoal === 'OFFSITE_CONVERSIONS' ? adset.conversionEvent : null,
      billingEvent: adset.billingEvent,
      startDate: toApiDate(adset.startDate),
      endDate: toApiDate(adset.endDate),
      targeting: buildTargetingJson(adset),
      placements: adset.placements,
      ads: adset.ads.map((ad) => ({
        name: ad.name,
        creativeAssetId: ad.creativeAssetId,
        copyVariantId: ad.copyVariantId,
        primaryText: ad.primaryText,
        headline: ad.headline,
        description: ad.description,
        ctaType: ad.ctaType,
        destinationUrl: ad.destinationUrl,
      })),
    })),
  }
}

function toApiDate(value: string | null | undefined) {
  if (!value) return null
  const trimmed = value.trim()
  return /^\d{4}-\d{2}-\d{2}$/.test(trimmed) ? trimmed : null
}

async function loadMetaConnectionStatus() {
  try {
    const { data } = await api.get(`/clients/${clientId.value}/meta/connection`)
    metaConnected.value = data?.status === 'CONNECTED'
    if (!metaConnected.value) {
      metaConnectionMessage.value = 'Meta is not connected for this client. Connect Meta to search targeting options.'
    }
  } catch {
    metaConnected.value = false
    metaConnectionMessage.value = 'Search unavailable. Meta is not connected for this client.'
  }
}

onMounted(() => {
  loadMetaConnectionStatus()
})
</script>

<style scoped>
.selected-creative-thumb {
  width: 80px;
  height: 80px;
  background: #f5f5f5;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.selected-creative-thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.package-import-card {
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.package-import-card:hover {
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  border-color: #7C4DFF;
}
</style>
