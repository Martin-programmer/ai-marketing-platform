<template>
  <div>
    <!-- Header -->
    <div class="d-flex align-center mb-6">
      <div>
        <h1 class="text-h4 font-weight-bold">My Profile</h1>
        <p class="text-body-1 text-medium-emphasis mt-1">Your client information and knowledge base</p>
      </div>
    </div>

    <!-- Loading -->
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Error -->
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- Profile Content -->
    <template v-if="!loading && profile">
      <!-- Client Info Card -->
      <v-card variant="outlined" class="mb-6">
        <v-card-title class="d-flex align-center">
          <v-icon start color="primary">mdi-domain</v-icon>
          Client Information
        </v-card-title>
        <v-divider />
        <v-card-text>
          <v-row>
            <v-col cols="12" md="6">
              <div class="mb-4">
                <div class="text-overline text-medium-emphasis">Company Name</div>
                <div class="text-body-1 font-weight-medium">{{ profile.companyName || '—' }}</div>
              </div>
            </v-col>
            <v-col cols="12" md="6">
              <div class="mb-4">
                <div class="text-overline text-medium-emphasis">Industry</div>
                <div class="text-body-1">{{ kb.industry || '—' }}</div>
              </div>
            </v-col>
            <v-col cols="12" md="6">
              <div class="mb-4">
                <div class="text-overline text-medium-emphasis">Website</div>
                <div class="text-body-1">
                  <a v-if="kb.website" :href="kb.website" target="_blank" class="text-decoration-none text-primary">
                    {{ kb.website }}
                    <v-icon size="14" class="ml-1">mdi-open-in-new</v-icon>
                  </a>
                  <span v-else>—</span>
                </div>
              </div>
            </v-col>
            <v-col cols="12" md="6">
              <div class="mb-4">
                <div class="text-overline text-medium-emphasis">Timezone</div>
                <div class="text-body-1">{{ kb.timezone || '—' }}</div>
              </div>
            </v-col>
            <v-col cols="12" md="6">
              <div class="mb-4">
                <div class="text-overline text-medium-emphasis">Currency</div>
                <div class="text-body-1">{{ kb.currency || '—' }}</div>
              </div>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <!-- Brand & Messaging -->
      <v-card variant="outlined" class="mb-6">
        <v-card-title class="d-flex align-center">
          <v-icon start color="deep-purple">mdi-palette</v-icon>
          Brand &amp; Messaging
        </v-card-title>
        <v-divider />
        <v-card-text>
          <div class="mb-4">
            <div class="text-overline text-medium-emphasis">Unique Selling Proposition</div>
            <p class="text-body-1">{{ kb.usp || '—' }}</p>
          </div>

          <div class="mb-4">
            <div class="text-overline text-medium-emphasis">Tone of Voice</div>
            <p class="text-body-1">{{ kb.tone || '—' }}</p>
          </div>

          <div v-if="kb.restrictions" class="mb-4">
            <div class="text-overline text-medium-emphasis">Restrictions / Guidelines</div>
            <p class="text-body-1">{{ kb.restrictions }}</p>
          </div>
        </v-card-text>
      </v-card>

      <!-- Target Audiences -->
      <v-card v-if="audiences.length > 0" variant="outlined" class="mb-6">
        <v-card-title class="d-flex align-center">
          <v-icon start color="blue">mdi-account-group</v-icon>
          Target Audiences
        </v-card-title>
        <v-divider />
        <v-card-text>
          <v-chip-group>
            <v-chip v-for="(aud, i) in audiences" :key="i" variant="tonal" color="blue" class="ma-1">
              {{ aud }}
            </v-chip>
          </v-chip-group>
        </v-card-text>
      </v-card>

      <!-- Current Offers -->
      <v-card v-if="offers.length > 0" variant="outlined" class="mb-6">
        <v-card-title class="d-flex align-center">
          <v-icon start color="green">mdi-tag-multiple</v-icon>
          Current Offers
        </v-card-title>
        <v-divider />
        <v-card-text>
          <v-list density="compact">
            <v-list-item v-for="(offer, i) in offers" :key="i" :title="offer" prepend-icon="mdi-check-circle" />
          </v-list>
        </v-card-text>
      </v-card>

      <!-- Competitors -->
      <v-card v-if="competitors.length > 0" variant="outlined" class="mb-6">
        <v-card-title class="d-flex align-center">
          <v-icon start color="orange">mdi-sword-cross</v-icon>
          Competitors
        </v-card-title>
        <v-divider />
        <v-card-text>
          <v-chip-group>
            <v-chip v-for="(comp, i) in competitors" :key="i" variant="tonal" color="orange" class="ma-1">
              {{ comp }}
            </v-chip>
          </v-chip-group>
        </v-card-text>
      </v-card>
    </template>

    <!-- Empty state -->
    <v-alert v-if="!loading && !profile && !error" type="info" variant="tonal">
      Profile information is not available. Please contact your agency.
    </v-alert>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import portalApi from '@/api/portal'

interface ProfileData {
  companyName: string
  profileJson: string | null
  [key: string]: unknown
}

interface KnowledgeBase {
  industry?: string
  timezone?: string
  currency?: string
  website?: string
  usp?: string
  tone?: string
  restrictions?: string
  audiences?: string[]
  offers?: string[]
  competitors?: string[]
}

const profile = ref<ProfileData | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const kb = computed<KnowledgeBase>(() => {
  if (!profile.value?.profileJson) return {}
  try {
    return JSON.parse(profile.value.profileJson) as KnowledgeBase
  } catch {
    return {}
  }
})

const audiences = computed(() => kb.value.audiences || [])
const offers = computed(() => kb.value.offers || [])
const competitors = computed(() => kb.value.competitors || [])

async function loadProfile() {
  loading.value = true
  error.value = null
  try {
    const res = await portalApi.getMyProfile()
    profile.value = res.data
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    error.value = err.response?.data?.message || 'Failed to load profile'
  } finally {
    loading.value = false
  }
}

onMounted(loadProfile)
</script>
