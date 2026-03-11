<template>
  <div>
    <div class="d-flex align-center mb-4">
      <v-btn icon variant="text" :to="`/clients/${clientId}`" class="mr-2">
        <v-icon>mdi-arrow-left</v-icon>
      </v-btn>
      <h1>Въпросник за клиент</h1>
      <v-spacer />
      <v-chip v-if="questionnaireCompleted" color="success" variant="tonal" class="ml-3">
        <v-icon start>mdi-check-circle</v-icon>
        Завършен
      </v-chip>
    </div>

    <!-- Completed banner -->
    <v-alert
      v-if="questionnaireCompleted"
      type="success"
      variant="tonal"
      class="mb-4"
    >
      <v-icon start>mdi-check-circle</v-icon>
      Въпросникът е завършен на {{ formatDate(questionnaireCompletedAt) }}
    </v-alert>

    <v-alert v-if="error" type="error" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <v-alert v-if="saveSuccess" type="success" variant="tonal" class="mb-4" closable @click:close="saveSuccess = false">
      Данните са запазени успешно!
    </v-alert>

    <!-- Progress indicator -->
    <v-card class="mb-4" variant="outlined">
      <v-card-text class="d-flex align-center">
        <span class="text-body-2 mr-3">Прогрес: {{ filledPercent }}%</span>
        <v-progress-linear :model-value="filledPercent" color="primary" height="8" rounded />
      </v-card-text>
    </v-card>

    <!-- Auto-fill from AI -->
    <v-card class="mb-4" variant="outlined">
      <v-card-text class="d-flex align-center flex-wrap ga-3">
        <v-icon color="deep-purple">mdi-robot</v-icon>
        <span class="text-body-2">Автоматично попълване от AI анализ на уебсайт</span>
        <v-spacer />
        <v-text-field
          v-model="aiBriefUrl"
          label="URL на уебсайт"
          placeholder="https://example.com"
          density="compact"
          hide-details
          class="mr-2"
          style="max-width: 340px"
        />
        <v-btn
          color="deep-purple"
          :loading="aiBriefLoading"
          :disabled="!aiBriefUrl"
          @click="onAiBrief"
        >
          <v-icon start>mdi-creation</v-icon>
          AI Auto-fill
        </v-btn>
      </v-card-text>
    </v-card>

    <!-- Main form -->
    <v-expansion-panels v-model="openPanels" multiple>
      <!-- Section 1: Basic Info -->
      <v-expansion-panel value="basic">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-account-card-details</v-icon>
          <strong>Основна информация</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.contactName"
                label="Име на контактното лице *"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.brandName"
                label="Име на бранда / бизнеса *"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="12">
              <v-text-field
                v-model="form.website"
                label="Уебсайт URL"
                placeholder="https://example.com"
              />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <!-- Section 2: Products & Services -->
      <v-expansion-panel value="products">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-package-variant</v-icon>
          <strong>Продукти и услуги</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12">
              <v-textarea
                v-model="form.productsDescription"
                label="Какви продукти или услуги продавате? *"
                rows="4"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.bestSellers"
                label="Основни продукти или бестселъри"
                rows="3"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.averageOrderValue"
                label="Средна стойност на поръчка"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.profitMargin"
                label="Марж на печалба"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.shippingInfo"
                label="Детайли за доставка и ценообразуване"
                rows="3"
              />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <!-- Section 3: Target Audience -->
      <v-expansion-panel value="audience">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-account-group</v-icon>
          <strong>Целева аудитория</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12">
              <v-textarea
                v-model="form.audiences"
                label="Кои са вашите клиенти? Демография и интереси *"
                rows="4"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.customerProblem"
                label="Какъв проблем решава вашият продукт?"
                rows="3"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.customerObjections"
                label="Какви възражения имат клиентите преди покупка?"
                rows="3"
              />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <!-- Section 4: Brand & Positioning -->
      <v-expansion-panel value="brand">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-star-circle</v-icon>
          <strong>Бранд и позициониране</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12">
              <v-textarea
                v-model="form.usp"
                label="Какво ви отличава от конкурентите? USP *"
                rows="4"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.competitors"
                label="Основни конкуренти или брандове, на които искате да приличате"
                rows="3"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.tone"
                label="Тон и усещане на бранда"
                rows="3"
              />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <!-- Section 5: Advertising & Marketing -->
      <v-expansion-panel value="advertising">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-bullhorn</v-icon>
          <strong>Реклама и маркетинг</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12">
              <v-textarea
                v-model="form.targetLocations"
                label="Целеви държави/локации за реклама *"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field
                v-model="form.adBudgetInfo"
                label="Дневен или месечен рекламен бюджет"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-select
                v-model="form.marketingGoal"
                label="Основна маркетингова цел *"
                :items="goalItems"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.previousAdExperience"
                label="Предишен рекламен опит (Meta, Google, TikTok)"
                rows="3"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.previousResults"
                label="Постигнати резултати до момента"
                rows="3"
              />
            </v-col>
            <v-col cols="12">
              <v-textarea
                v-model="form.currentChallenges"
                label="Текущи основни предизвикателства"
                rows="3"
              />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <!-- Section 6: Materials & Infrastructure -->
      <v-expansion-panel value="materials">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-folder-image</v-icon>
          <strong>Материали и инфраструктура</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12" md="6">
              <v-select
                v-model="form.hasCreatives"
                label="Имате ли рекламни криейтиви?"
                :items="creativesItems"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-select
                v-model="form.hasTracking"
                label="Имате ли Meta Pixel / тракинг?"
                :items="trackingItems"
              />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>
    </v-expansion-panels>

    <!-- Action buttons -->
    <div class="d-flex justify-end mt-6 ga-3">
      <span v-if="lastAutoSave" class="text-caption text-grey align-self-center mr-3">
        Автоматично запазено: {{ formatTime(lastAutoSave) }}
      </span>
      <v-btn variant="outlined" :loading="saving" @click="onSaveDraft">
        <v-icon start>mdi-content-save-outline</v-icon>
        Запази чернова
      </v-btn>
      <v-btn color="primary" :loading="saving" @click="onComplete">
        <v-icon start>mdi-check-circle</v-icon>
        Завърши въпросника
      </v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/client'

const route = useRoute()
const clientId = computed(() => route.params.clientId as string)

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const saveSuccess = ref(false)
const aiBriefLoading = ref(false)
const aiBriefUrl = ref('')
const lastAutoSave = ref<Date | null>(null)
const questionnaireCompleted = ref(false)
const questionnaireCompletedAt = ref<string | null>(null)

const openPanels = ref(['basic', 'products', 'audience', 'brand', 'advertising', 'materials'])

const goalItems = ['Sales', 'Leads', 'Traffic', 'Brand Awareness', 'Other']
const creativesItems = [
  { title: 'Да', value: 'Yes' },
  { title: 'Не', value: 'No' },
  { title: 'Частично', value: 'Partially' },
]
const trackingItems = [
  { title: 'Да', value: 'Yes' },
  { title: 'Не', value: 'No' },
  { title: 'Не знам', value: "Don't know" },
]

const rules = {
  required: (v: string) => !!v || 'Това поле е задължително',
}

interface QuestionnaireForm {
  contactName: string
  brandName: string
  website: string
  productsDescription: string
  bestSellers: string
  averageOrderValue: string
  profitMargin: string
  shippingInfo: string
  audiences: string
  customerProblem: string
  customerObjections: string
  usp: string
  competitors: string
  tone: string
  targetLocations: string
  adBudgetInfo: string
  marketingGoal: string
  previousAdExperience: string
  previousResults: string
  currentChallenges: string
  hasCreatives: string
  hasTracking: string
}

const emptyForm = (): QuestionnaireForm => ({
  contactName: '',
  brandName: '',
  website: '',
  productsDescription: '',
  bestSellers: '',
  averageOrderValue: '',
  profitMargin: '',
  shippingInfo: '',
  audiences: '',
  customerProblem: '',
  customerObjections: '',
  usp: '',
  competitors: '',
  tone: '',
  targetLocations: '',
  adBudgetInfo: '',
  marketingGoal: '',
  previousAdExperience: '',
  previousResults: '',
  currentChallenges: '',
  hasCreatives: '',
  hasTracking: '',
})

const form = ref<QuestionnaireForm>(emptyForm())

const allFields: (keyof QuestionnaireForm)[] = [
  'contactName', 'brandName', 'website', 'productsDescription', 'bestSellers',
  'averageOrderValue', 'profitMargin', 'shippingInfo', 'audiences', 'customerProblem',
  'customerObjections', 'usp', 'competitors', 'tone', 'targetLocations', 'adBudgetInfo',
  'marketingGoal', 'previousAdExperience', 'previousResults', 'currentChallenges',
  'hasCreatives', 'hasTracking',
]

const requiredFields: (keyof QuestionnaireForm)[] = [
  'contactName', 'brandName', 'productsDescription', 'audiences', 'usp',
  'targetLocations', 'marketingGoal',
]

const filledPercent = computed(() => {
  const filled = allFields.filter(f => !!form.value[f]).length
  return Math.round((filled / allFields.length) * 100)
})

// ---- LocalStorage auto-save ----
const LS_KEY = computed(() => `questionnaire_draft_${clientId.value}`)

function saveDraftToLocalStorage() {
  try {
    localStorage.setItem(LS_KEY.value, JSON.stringify(form.value))
    lastAutoSave.value = new Date()
  } catch { /* ignore */ }
}

function loadDraftFromLocalStorage(): QuestionnaireForm | null {
  try {
    const raw = localStorage.getItem(LS_KEY.value)
    return raw ? JSON.parse(raw) : null
  } catch { return null }
}

function clearLocalDraft() {
  try { localStorage.removeItem(LS_KEY.value) } catch { /* ignore */ }
}

// Auto-save every 30 seconds
let autoSaveInterval: ReturnType<typeof setInterval> | null = null

watch(form, () => {
  // mark that changes exist - auto-save will pick them up
}, { deep: true })

// ---- API calls ----
async function fetchQuestionnaire() {
  loading.value = true
  try {
    const { data } = await api.get(`/clients/${clientId.value}/questionnaire`)
    if (data && Object.keys(data).length > 0) {
      // Merge server data with form
      const serverData = data as Record<string, any>
      questionnaireCompleted.value = !!serverData.questionnaireCompleted
      questionnaireCompletedAt.value = serverData.questionnaireCompletedAt || null

      for (const key of allFields) {
        if (serverData[key]) {
          (form.value as any)[key] = serverData[key]
        }
      }
    }

    // Also try to pre-fill from profile data (AI Briefer results)
    try {
      const { data: profileData } = await api.get(`/clients/${clientId.value}/profile`)
      if (profileData?.profileJson) {
        const pj = profileData.profileJson
        if (!form.value.website && (profileData.website || pj.website)) {
          form.value.website = profileData.website || pj.website || ''
        }
        if (!form.value.usp && pj.usp) form.value.usp = pj.usp
        if (!form.value.tone && pj.tone_of_voice) form.value.tone = pj.tone_of_voice
        if (!form.value.audiences && pj.target_audiences) {
          form.value.audiences = Array.isArray(pj.target_audiences)
            ? pj.target_audiences.join(', ')
            : pj.target_audiences
        }
        if (!form.value.competitors && pj.competitors) {
          form.value.competitors = Array.isArray(pj.competitors)
            ? pj.competitors.join(', ')
            : pj.competitors
        }
        if (!form.value.productsDescription && pj.offers) {
          form.value.productsDescription = Array.isArray(pj.offers)
            ? pj.offers.join(', ')
            : pj.offers
        }
      }
    } catch { /* no profile yet */ }

    // Override with local draft if newer
    const draft = loadDraftFromLocalStorage()
    if (draft && !questionnaireCompleted.value) {
      // Use local draft values for any field that's non-empty but was empty from server
      for (const key of allFields) {
        if (draft[key] && !form.value[key]) {
          (form.value as any)[key] = draft[key]
        }
      }
    }
  } catch (e: any) {
    // 404 is fine — no questionnaire yet
    if (e.response?.status !== 404) {
      error.value = e.response?.data?.message || e.message
    }
  } finally {
    loading.value = false
  }
}

async function saveToBackend(complete: boolean) {
  saving.value = true
  error.value = null
  saveSuccess.value = false
  try {
    const { data } = await api.put(
      `/clients/${clientId.value}/questionnaire?complete=${complete}`,
      form.value
    )
    if (complete) {
      questionnaireCompleted.value = true
      questionnaireCompletedAt.value = data.questionnaireCompletedAt
      clearLocalDraft()
    }
    saveSuccess.value = true
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message
  } finally {
    saving.value = false
  }
}

async function onSaveDraft() {
  await saveToBackend(false)
  saveDraftToLocalStorage()
}

function validateRequired(): boolean {
  for (const field of requiredFields) {
    if (!form.value[field]) {
      error.value = 'Моля, попълнете всички задължителни полета (маркирани с *)'
      return false
    }
  }
  return true
}

async function onComplete() {
  if (!validateRequired()) return
  await saveToBackend(true)
}

async function onAiBrief() {
  if (!aiBriefUrl.value) return
  aiBriefLoading.value = true
  error.value = null
  try {
    const { data } = await api.post(`/clients/${clientId.value}/ai-brief`, {
      websiteUrl: aiBriefUrl.value,
    })
    if (data && !data.error) {
      // Populate form fields from AI analysis
      if (data.usp && !form.value.usp) form.value.usp = data.usp
      if (data.tone_of_voice && !form.value.tone) form.value.tone = data.tone_of_voice
      if (data.target_audiences && !form.value.audiences) {
        form.value.audiences = Array.isArray(data.target_audiences)
          ? data.target_audiences.join(', ')
          : data.target_audiences
      }
      if (data.competitors && !form.value.competitors) {
        form.value.competitors = Array.isArray(data.competitors)
          ? data.competitors.join(', ')
          : data.competitors
      }
      if (data.offers && !form.value.productsDescription) {
        form.value.productsDescription = Array.isArray(data.offers)
          ? data.offers.join(', ')
          : data.offers
      }
      if (data.suggested_monthly_budget_range && !form.value.adBudgetInfo) {
        form.value.adBudgetInfo = data.suggested_monthly_budget_range
      }
      if (aiBriefUrl.value && !form.value.website) {
        form.value.website = aiBriefUrl.value
      }
    } else if (data?.error) {
      error.value = data.error
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message
  } finally {
    aiBriefLoading.value = false
  }
}

function formatDate(val: string | null) {
  if (!val) return ''
  return new Date(val).toLocaleDateString('bg-BG', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function formatTime(date: Date) {
  return date.toLocaleTimeString('bg-BG', { hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  fetchQuestionnaire()

  // Auto-save every 30 seconds
  autoSaveInterval = setInterval(() => {
    if (!questionnaireCompleted.value) {
      saveDraftToLocalStorage()
    }
  }, 30000)
})

onUnmounted(() => {
  if (autoSaveInterval) clearInterval(autoSaveInterval)
})
</script>
