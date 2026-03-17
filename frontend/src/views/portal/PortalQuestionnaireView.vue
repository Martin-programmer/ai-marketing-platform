<template>
  <div>
    <div class="d-flex align-center mb-4 flex-wrap ga-3">
      <h1>Client Questionnaire</h1>
      <v-chip v-if="questionnaireCompleted" color="success" variant="tonal">
        <v-icon start>mdi-check-circle</v-icon>
        Completed
      </v-chip>
    </div>

    <v-alert v-if="questionnaireCompleted" type="success" variant="tonal" class="mb-4">
      Completed on {{ formatDate(questionnaireCompletedAt) }}
    </v-alert>

    <v-alert v-if="error" type="error" class="mb-4" closable @click:close="error = null">
      {{ error }}
    </v-alert>

    <v-alert v-if="saveSuccess" type="success" variant="tonal" class="mb-4" closable @click:close="saveSuccess = false">
      {{ saveSuccessMessage }}
    </v-alert>

    <v-card class="mb-4" variant="outlined">
      <v-card-text class="d-flex align-center ga-3 flex-wrap">
        <span class="text-body-2">Progress: {{ filledPercent }}%</span>
        <v-progress-linear :model-value="filledPercent" color="primary" height="8" rounded />
      </v-card-text>
    </v-card>

    <v-expansion-panels v-model="openPanels" multiple>
      <v-expansion-panel value="basic">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-account-card-details</v-icon>
          <strong>Basic Information</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12" md="6">
              <v-text-field v-model="form.contactName" label="Contact Name *" :rules="[rules.required]" />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field v-model="form.brandName" label="Brand / Business Name *" :rules="[rules.required]" />
            </v-col>
            <v-col cols="12">
              <v-text-field v-model="form.website" label="Website URL" placeholder="https://example.com" />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <v-expansion-panel value="products">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-package-variant</v-icon>
          <strong>Products and Services</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12">
              <v-textarea v-model="form.productsDescription" label="What products or services do you sell? *" rows="4" :rules="[rules.required]" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.bestSellers" label="Main products or best sellers" rows="3" />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field v-model="form.averageOrderValue" label="Average order value" />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field v-model="form.profitMargin" label="Profit margin" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.shippingInfo" label="Shipping and pricing details" rows="3" />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <v-expansion-panel value="audience">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-account-group</v-icon>
          <strong>Target Audience</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12">
              <v-textarea v-model="form.audiences" label="Who are your customers? Demographics and interests *" rows="4" :rules="[rules.required]" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.customerProblem" label="What problem does your product solve?" rows="3" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.customerObjections" label="What objections do customers have before buying?" rows="3" />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <v-expansion-panel value="brand">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-star-circle</v-icon>
          <strong>Brand and Positioning</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12">
              <v-textarea v-model="form.usp" label="What makes you different from competitors? USP *" rows="4" :rules="[rules.required]" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.competitors" label="Main competitors or brands you want to be compared to" rows="3" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.tone" label="Brand tone and feel" rows="3" />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <v-expansion-panel value="advertising">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-bullhorn</v-icon>
          <strong>Advertising and Marketing</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12">
              <v-textarea v-model="form.targetLocations" label="Target countries / locations for ads *" :rules="[rules.required]" />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field v-model="form.adBudgetInfo" label="Daily or monthly ad budget" />
            </v-col>
            <v-col cols="12" md="6">
              <v-select v-model="form.marketingGoal" label="Main marketing goal *" :items="goalItems" :rules="[rules.required]" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.previousAdExperience" label="Previous advertising experience (Meta, Google, TikTok)" rows="3" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.previousResults" label="Results achieved so far" rows="3" />
            </v-col>
            <v-col cols="12">
              <v-textarea v-model="form.currentChallenges" label="Current main challenges" rows="3" />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>

      <v-expansion-panel value="materials">
        <v-expansion-panel-title>
          <v-icon class="mr-2">mdi-folder-image</v-icon>
          <strong>Materials and Infrastructure</strong>
        </v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-row dense>
            <v-col cols="12" md="6">
              <v-select v-model="form.hasCreatives" label="Do you have ad creatives?" :items="yesNoPartialItems" />
            </v-col>
            <v-col cols="12" md="6">
              <v-select v-model="form.hasTracking" label="Do you have Meta Pixel / tracking?" :items="trackingItems" />
            </v-col>
          </v-row>
        </v-expansion-panel-text>
      </v-expansion-panel>
    </v-expansion-panels>

    <div class="d-flex justify-end mt-6 ga-3 flex-wrap">
      <span v-if="lastAutoSave" class="text-caption text-medium-emphasis align-self-center mr-3">
        Auto-saved: {{ formatTime(lastAutoSave) }}
      </span>
      <v-btn variant="outlined" :loading="saving" @click="onSaveDraft">
        <v-icon start>mdi-content-save-outline</v-icon>
        Save Draft
      </v-btn>
      <v-btn color="primary" :loading="saving" @click="onComplete">
        <v-icon start>mdi-check-circle</v-icon>
        Complete Questionnaire
      </v-btn>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import portalApi from '@/api/portal'

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const saveSuccess = ref(false)
const saveSuccessMessage = ref('Saved successfully!')
const lastAutoSave = ref<Date | null>(null)
const questionnaireCompleted = ref(false)
const questionnaireCompletedAt = ref<string | null>(null)
const openPanels = ref(['basic', 'products', 'audience', 'brand', 'advertising', 'materials'])

const goalItems = ['Sales', 'Leads', 'Traffic', 'Brand Awareness', 'Other']
const yesNoPartialItems = [
  { title: 'Yes', value: 'Yes' },
  { title: 'No', value: 'No' },
  { title: 'Partially', value: 'Partially' },
]
const trackingItems = [
  { title: 'Yes', value: 'Yes' },
  { title: 'No', value: 'No' },
  { title: "Don't know", value: "Don't know" },
]

const rules = {
  required: (v: string) => !!v || 'This field is required',
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

type QuestionnaireResponse = Partial<QuestionnaireForm> & {
  questionnaireCompleted?: boolean
  questionnaireCompletedAt?: string | null
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
  'contactName', 'brandName', 'productsDescription', 'audiences', 'usp', 'targetLocations', 'marketingGoal',
]

const filledPercent = computed(() => {
  const filled = allFields.filter((field) => !!form.value[field]).length
  return Math.round((filled / allFields.length) * 100)
})

const LS_KEY = 'portal_questionnaire_draft'

function saveDraftToLocalStorage() {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify(form.value))
    lastAutoSave.value = new Date()
  } catch {
    // ignore
  }
}

function loadDraftFromLocalStorage(): QuestionnaireForm | null {
  try {
    const raw = localStorage.getItem(LS_KEY)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function clearLocalDraft() {
  try {
    localStorage.removeItem(LS_KEY)
  } catch {
    // ignore
  }
}

let autoSaveInterval: ReturnType<typeof setInterval> | null = null

watch(form, () => {
  // tracked for autosave
}, { deep: true })

async function fetchQuestionnaire() {
  loading.value = true
  error.value = null
  try {
    const { data } = await portalApi.getQuestionnaire()
    const serverData = data as QuestionnaireResponse
    if (serverData && Object.keys(serverData).length > 0) {
      questionnaireCompleted.value = !!serverData.questionnaireCompleted
      questionnaireCompletedAt.value = serverData.questionnaireCompletedAt || null
      for (const key of allFields) {
        const value = serverData[key]
        if (value != null) {
          form.value[key] = value
        }
      }
    }

    const draft = loadDraftFromLocalStorage()
    if (draft && !questionnaireCompleted.value) {
      for (const key of allFields) {
        if (draft[key] && !form.value[key]) {
          form.value[key] = draft[key]
        }
      }
    }
  } catch (e: unknown) {
    const message = extractErrorMessage(e)
    const status = typeof e === 'object' && e !== null && 'response' in e
      ? (e as { response?: { status?: number } }).response?.status
      : undefined
    if (status !== 404) {
      error.value = message
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
    const { data } = await portalApi.saveQuestionnaire(form.value, complete)
    const serverData = data as QuestionnaireResponse
    questionnaireCompleted.value = !!serverData.questionnaireCompleted
    questionnaireCompletedAt.value = serverData.questionnaireCompletedAt || null
    if (complete) {
      clearLocalDraft()
      saveSuccessMessage.value = 'Questionnaire completed successfully!'
    } else {
      saveSuccessMessage.value = 'Draft saved successfully!'
    }
    saveSuccess.value = true
    window.dispatchEvent(new Event('portal-questionnaire-updated'))
    setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (e: unknown) {
    error.value = extractErrorMessage(e)
  } finally {
    saving.value = false
  }
}

function validateRequired() {
  for (const field of requiredFields) {
    if (!form.value[field]) {
      error.value = 'Please fill in all required fields marked with *.'
      return false
    }
  }
  return true
}

async function onSaveDraft() {
  await saveToBackend(false)
  saveDraftToLocalStorage()
}

async function onComplete() {
  if (!validateRequired()) return
  await saveToBackend(true)
}

function formatDate(value: string | null) {
  if (!value) return ''
  return new Date(value).toLocaleDateString('en-US', {
    year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function formatTime(value: Date) {
  return value.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })
}

function extractErrorMessage(errorValue: unknown) {
  if (typeof errorValue === 'object' && errorValue !== null && 'response' in errorValue) {
    const response = (errorValue as { response?: { data?: { message?: string } } }).response
    if (response?.data?.message) {
      return response.data.message
    }
  }

  if (errorValue instanceof Error) {
    return errorValue.message
  }

  return 'An unexpected error occurred.'
}

onMounted(() => {
  fetchQuestionnaire()
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
