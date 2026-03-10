<template>
  <div>
    <div class="d-flex justify-space-between align-center mb-4">
      <h1>Clients</h1>
      <v-btn color="primary" @click="showCreate = true">
        <v-icon start>mdi-plus</v-icon>
        New Client
      </v-btn>
    </div>

    <v-alert v-if="store.error" type="error" class="mb-4">{{ store.error }}</v-alert>

    <v-card>
      <v-data-table
        :headers="headers"
        :items="store.clients"
        :loading="store.loading"
        item-value="id"
        hover
      >
        <template #item.status="{ item }">
          <v-chip :color="item.status === 'ACTIVE' ? 'success' : 'warning'" size="small">
            {{ item.status }}
          </v-chip>
        </template>
        <template #item.createdAt="{ item }">
          {{ new Date(item.createdAt).toLocaleDateString() }}
        </template>
        <template #item.actions="{ item }">
          <v-btn size="small" variant="text" :to="`/clients/${item.id}`">
            <v-icon>mdi-eye</v-icon>
          </v-btn>
          <v-btn size="small" variant="text" color="deep-purple" @click="openBriefDialog(item)"
                 title="AI Auto-fill Profile">
            <v-icon>mdi-robot</v-icon>
          </v-btn>
          <v-btn size="small" variant="text" color="teal" @click="openAudienceDialog(item)"
                 title="Suggest Audiences">
            <v-icon>mdi-account-group</v-icon>
          </v-btn>
          <v-btn v-if="item.status === 'ACTIVE'" size="small" variant="text" color="warning" @click="store.pauseClient(item.id)">
            <v-icon>mdi-pause</v-icon>
          </v-btn>
          <v-btn v-else size="small" variant="text" color="success" @click="store.activateClient(item.id)">
            <v-icon>mdi-play</v-icon>
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <!-- Create Dialog -->
    <v-dialog v-model="showCreate" max-width="500">
      <v-card title="New Client">
        <v-card-text>
          <v-text-field v-model="form.name" label="Name" required />
          <v-text-field v-model="form.industry" label="Industry" />
          <v-text-field v-model="form.timezone" label="Timezone" placeholder="Europe/Sofia" />
          <v-text-field v-model="form.currency" label="Currency" placeholder="BGN" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreate = false">Cancel</v-btn>
          <v-btn color="primary" @click="onCreate">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- AI Brief Dialog -->
    <v-dialog v-model="showBriefDialog" max-width="700">
      <v-card>
        <v-card-title class="d-flex align-center">
          <v-icon color="deep-purple" class="mr-2">mdi-robot</v-icon>
          AI Auto-fill Profile — {{ selectedClient?.name }}
        </v-card-title>
        <v-card-text>
          <v-text-field
            v-model="websiteUrl"
            label="Website URL"
            placeholder="https://example.com"
            prepend-icon="mdi-web"
            :disabled="store.briefLoading"
          />
          <v-btn
            color="deep-purple"
            :loading="store.briefLoading"
            :disabled="!websiteUrl"
            block
            class="mb-4"
            @click="onAnalyzeWebsite"
          >
            <v-icon start>mdi-magnify</v-icon>
            Analyse Website
          </v-btn>

          <!-- Results -->
          <template v-if="store.briefResult && !store.briefResult.error">
            <v-alert type="success" variant="tonal" class="mb-3">
              Profile auto-filled successfully! Review the data below.
            </v-alert>

            <v-row dense>
              <v-col cols="6">
                <v-text-field v-model="store.briefResult.industry" label="Industry" readonly variant="outlined" density="compact" />
              </v-col>
              <v-col cols="6">
                <v-text-field v-model="store.briefResult.business_model" label="Business Model" readonly variant="outlined" density="compact" />
              </v-col>
            </v-row>
            <v-textarea v-model="store.briefResult.usp" label="USP" readonly rows="2" variant="outlined" density="compact" class="mb-2" />
            <v-textarea v-model="store.briefResult.tone_of_voice" label="Tone of Voice" readonly rows="2" variant="outlined" density="compact" class="mb-2" />
            <v-textarea v-model="store.briefResult.suggested_strategy" label="Suggested Strategy" readonly rows="2" variant="outlined" density="compact" class="mb-2" />

            <v-row dense>
              <v-col cols="6">
                <div class="text-subtitle-2 mb-1">Target Audiences</div>
                <v-chip v-for="(a, i) in store.briefResult.target_audiences" :key="i" size="small" class="mr-1 mb-1" color="primary" variant="tonal">{{ a }}</v-chip>
              </v-col>
              <v-col cols="6">
                <div class="text-subtitle-2 mb-1">Products / Offers</div>
                <v-chip v-for="(o, i) in store.briefResult.offers" :key="i" size="small" class="mr-1 mb-1" color="teal" variant="tonal">{{ o }}</v-chip>
              </v-col>
            </v-row>

            <div v-if="store.briefResult.competitors?.length" class="mt-2">
              <div class="text-subtitle-2 mb-1">Competitors</div>
              <v-chip v-for="(c, i) in store.briefResult.competitors" :key="i" size="small" class="mr-1 mb-1" variant="outlined">{{ c }}</v-chip>
            </div>

            <v-text-field v-if="store.briefResult.suggested_monthly_budget_range" v-model="store.briefResult.suggested_monthly_budget_range"
                          label="Suggested Monthly Budget" readonly variant="outlined" density="compact" class="mt-3" />
          </template>

          <v-alert v-else-if="store.briefResult?.error" type="error" variant="tonal">
            {{ store.briefResult.error }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showBriefDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Audience Architect Dialog -->
    <v-dialog v-model="showAudienceDialog" max-width="900" scrollable>
      <v-card>
        <v-card-title class="d-flex align-center">
          <v-icon color="teal" class="mr-2">mdi-account-group</v-icon>
          Audience Suggestions — {{ selectedClient?.name }}
        </v-card-title>
        <v-card-text>
          <v-btn
            v-if="!store.audienceResult"
            color="teal"
            :loading="store.audienceLoading"
            block
            class="mb-4"
            @click="onSuggestAudiences"
          >
            <v-icon start>mdi-creation</v-icon>
            Generate Audience Suggestions
          </v-btn>

          <template v-if="store.audienceResult && !store.audienceResult.error">
            <!-- Strategy Notes -->
            <v-alert v-if="store.audienceResult.strategy_notes" type="info" variant="tonal" class="mb-4">
              <strong>Strategy:</strong> {{ store.audienceResult.strategy_notes }}
            </v-alert>

            <!-- Audience Cards -->
            <v-row>
              <v-col v-for="(aud, i) in store.audienceResult.recommended_audiences" :key="i" cols="12" md="6">
                <v-card variant="outlined" class="h-100">
                  <v-card-title class="text-subtitle-1 d-flex align-center">
                    {{ aud.name }}
                    <v-spacer />
                    <v-chip :color="aud.confidence === 'HIGH' ? 'success' : aud.confidence === 'MEDIUM' ? 'warning' : 'grey'"
                            size="x-small">{{ aud.confidence }}</v-chip>
                  </v-card-title>
                  <v-card-text>
                    <div class="text-body-2 mb-2">{{ aud.description }}</div>
                    <div class="text-caption text-medium-emphasis mb-1">
                      <v-icon size="14" class="mr-1">mdi-target</v-icon>
                      <strong>Funnel:</strong> {{ aud.funnel_stage }}
                    </div>
                    <div class="text-caption text-medium-emphasis mb-1">
                      <v-icon size="14" class="mr-1">mdi-account-multiple</v-icon>
                      <strong>Est. Size:</strong> {{ aud.estimated_size }}
                    </div>
                    <div class="text-caption text-medium-emphasis mb-1">
                      <v-icon size="14" class="mr-1">mdi-currency-usd</v-icon>
                      <strong>Budget:</strong> {{ aud.suggested_daily_budget }}
                    </div>
                    <v-divider class="my-2" />
                    <div class="text-caption"><strong>Rationale:</strong> {{ aud.rationale }}</div>

                    <div v-if="aud.targeting?.interests?.length" class="mt-2">
                      <v-chip v-for="(int_item, j) in aud.targeting.interests.slice(0, 5)" :key="j"
                              size="x-small" class="mr-1 mb-1" color="primary" variant="tonal">
                        {{ int_item.name || int_item }}
                      </v-chip>
                    </div>
                  </v-card-text>
                </v-card>
              </v-col>
            </v-row>

            <!-- Exclusion Recommendations -->
            <div v-if="store.audienceResult.exclusion_recommendations?.length" class="mt-4">
              <div class="text-subtitle-2 mb-2">Exclusion Recommendations</div>
              <v-alert v-for="(ex, i) in store.audienceResult.exclusion_recommendations" :key="i"
                       type="warning" variant="tonal" density="compact" class="mb-2">
                {{ ex.description }}
              </v-alert>
            </div>

            <!-- Overlap Warnings -->
            <div v-if="store.audienceResult.overlap_warnings?.length" class="mt-3">
              <div class="text-subtitle-2 mb-2">Overlap Warnings</div>
              <v-alert v-for="(w, i) in store.audienceResult.overlap_warnings" :key="i"
                       type="info" variant="tonal" density="compact" class="mb-2">
                {{ w }}
              </v-alert>
            </div>

            <v-btn color="teal" variant="outlined" class="mt-4" @click="onSuggestAudiences">
              <v-icon start>mdi-refresh</v-icon>
              Regenerate
            </v-btn>
          </template>

          <v-alert v-else-if="store.audienceResult?.error" type="error" variant="tonal">
            {{ store.audienceResult.error }}
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showAudienceDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useClientStore } from '@/stores/clients'
import type { Client } from '@/stores/clients'

const store = useClientStore()
const showCreate = ref(false)
const form = ref({ name: '', industry: '', timezone: 'Europe/Sofia', currency: 'BGN' })

// AI Brief state
const showBriefDialog = ref(false)
const selectedClient = ref<Client | null>(null)
const websiteUrl = ref('')

// Audience state
const showAudienceDialog = ref(false)

const headers = [
  { title: 'Name', key: 'name' },
  { title: 'Industry', key: 'industry' },
  { title: 'Status', key: 'status' },
  { title: 'Currency', key: 'currency' },
  { title: 'Created', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false },
]

onMounted(() => store.fetchClients())

async function onCreate() {
  await store.createClient(form.value)
  showCreate.value = false
  form.value = { name: '', industry: '', timezone: 'Europe/Sofia', currency: 'BGN' }
}

function openBriefDialog(client: Client) {
  selectedClient.value = client
  websiteUrl.value = ''
  store.briefResult = null
  showBriefDialog.value = true
}

async function onAnalyzeWebsite() {
  if (!selectedClient.value || !websiteUrl.value) return
  await store.analyzeWebsite(selectedClient.value.id, websiteUrl.value)
}

function openAudienceDialog(client: Client) {
  selectedClient.value = client
  store.audienceResult = null
  showAudienceDialog.value = true
}

async function onSuggestAudiences() {
  if (!selectedClient.value) return
  await store.suggestAudiences(selectedClient.value.id)
}
</script>
