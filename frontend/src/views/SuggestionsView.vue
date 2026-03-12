<template>
  <div>
    <div class="d-flex align-center flex-wrap ga-3 mb-4">
      <h1>AI Suggestions</h1>
      <v-spacer />
      <v-chip variant="tonal" color="primary">
        {{ filteredSuggestions.length }} visible
      </v-chip>
    </div>

    <v-card class="mb-4" variant="outlined">
      <v-card-text>
        <v-row>
          <v-col cols="12" md="4">
            <v-select
              v-model="selectedClient"
              :items="clientStore.clients"
              item-title="name"
              item-value="id"
              label="Client"
              variant="outlined"
              density="compact"
              hide-details
              @update:model-value="onClientChange"
            />
          </v-col>
          <v-col cols="12" md="8">
            <div class="text-caption text-medium-emphasis mb-2">Type</div>
            <div class="d-flex flex-wrap ga-2">
              <v-chip
                v-for="option in typeOptions"
                :key="option.value"
                :color="selectedType === option.value ? typeColor(option.value) : undefined"
                :variant="selectedType === option.value ? 'flat' : 'outlined'"
                @click="setTypeFilter(option.value)"
              >
                {{ option.label }}
              </v-chip>
            </div>
          </v-col>
          <v-col cols="12" md="7">
            <div class="text-caption text-medium-emphasis mb-2">Status</div>
            <div class="d-flex flex-wrap ga-2">
              <v-chip
                v-for="option in statusOptions"
                :key="option.value ?? 'all'"
                :color="selectedStatus === option.value ? statusColor(option.value) : undefined"
                :variant="selectedStatus === option.value ? 'flat' : 'outlined'"
                @click="setStatusFilter(option.value)"
              >
                {{ option.label }}
              </v-chip>
            </div>
          </v-col>
          <v-col cols="12" md="5">
            <div class="text-caption text-medium-emphasis mb-2">Date range</div>
            <div class="d-flex flex-wrap ga-2 align-center mb-2">
              <v-chip
                v-for="option in dateRangeOptions"
                :key="option.value"
                :color="selectedDatePreset === option.value ? 'primary' : undefined"
                :variant="selectedDatePreset === option.value ? 'flat' : 'outlined'"
                @click="setDatePreset(option.value)"
              >
                {{ option.label }}
              </v-chip>
            </div>
            <div v-if="selectedDatePreset === 'custom'" class="d-flex ga-2 flex-wrap">
              <v-text-field
                v-model="customFrom"
                label="From"
                type="date"
                variant="outlined"
                density="compact"
                hide-details
              />
              <v-text-field
                v-model="customTo"
                label="To"
                type="date"
                variant="outlined"
                density="compact"
                hide-details
              />
            </div>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <v-card class="mb-4" variant="tonal" color="grey-lighten-4">
      <v-card-text class="d-flex flex-wrap align-center ga-2 summary-bar">
        <span class="text-subtitle-2">Summary:</span>
        <v-btn
          v-for="item in summaryStatusItems"
          :key="item.status"
          variant="text"
          size="small"
          :color="selectedStatus === item.status ? statusColor(item.status) : 'default'"
          @click="setStatusFilter(item.status)"
        >
          {{ item.count }} {{ item.label }}
        </v-btn>
        <v-btn variant="text" size="small" @click="setStatusFilter(null)">Clear status</v-btn>
      </v-card-text>
    </v-card>

    <v-progress-linear v-if="store.loading" indeterminate color="primary" class="mb-4" />

    <v-alert v-if="store.error" type="error" class="mb-4">{{ store.error }}</v-alert>

    <v-alert v-if="!selectedClient" type="info" class="mb-4">
      Select a client to view AI suggestions.
    </v-alert>

    <template v-else>
      <v-row v-if="pagedSuggestions.length">
        <v-col
          v-for="suggestion in pagedSuggestions"
          :key="suggestion.id"
          cols="12"
          md="6"
          xl="4"
        >
          <v-card
            class="suggestion-card h-100"
            :class="{ 'suggestion-card-muted': suggestion.status === 'REJECTED' }"
            variant="outlined"
            @click="openSuggestionDetail(suggestion)"
          >
            <div class="suggestion-card-bar" :style="{ backgroundColor: typeHexColor(suggestion.suggestionType) }" />
            <v-card-text class="pl-6">
              <div class="d-flex align-start ga-2 mb-3 flex-wrap">
                <div>
                  <div class="text-subtitle-1 font-weight-medium d-flex align-center ga-2 flex-wrap">
                    <v-icon size="18" :color="typeColor(suggestion.suggestionType)">{{ typeIcon(suggestion.suggestionType) }}</v-icon>
                    <span>{{ humanize(suggestion.suggestionType) }}</span>
                  </div>
                  <div class="text-caption text-medium-emphasis">
                    {{ scopeLabel(suggestion) }}
                  </div>
                </div>
                <v-spacer />
                <v-chip :color="riskColor(suggestion.riskLevel)" size="small">{{ suggestion.riskLevel }}</v-chip>
                <v-chip :color="statusColor(suggestion.status)" size="small" variant="tonal">{{ suggestion.status }}</v-chip>
              </div>

              <div class="d-flex align-center flex-wrap ga-2 mb-3">
                <v-chip size="x-small" variant="outlined">{{ clientNameFor(suggestion.clientId) }}</v-chip>
                <span class="text-caption text-medium-emphasis">{{ formatRelativeTime(suggestion.createdAt) }}</span>
              </div>

              <div class="mb-3">
                <div class="d-flex justify-space-between text-caption mb-1">
                  <span>Confidence</span>
                  <span>{{ confidencePercent(suggestion.confidence) }}%</span>
                </div>
                <v-progress-linear
                  :model-value="confidencePercent(suggestion.confidence)"
                  :color="confidencePercent(suggestion.confidence) >= 75 ? 'success' : confidencePercent(suggestion.confidence) >= 50 ? 'warning' : 'error'"
                  rounded
                  height="8"
                />
              </div>

              <div class="text-body-2 mb-3 rationale-preview">
                {{ suggestion.rationale || 'No rationale provided.' }}
              </div>

              <v-alert density="compact" variant="tonal" :color="typeColor(suggestion.suggestionType)" class="mb-3">
                {{ payloadSummary(suggestion) }}
              </v-alert>

              <div v-if="suggestion.status === 'REJECTED'" class="text-caption text-medium-emphasis mb-2">
                Rejected {{ suggestion.reviewedAt ? formatRelativeTime(suggestion.reviewedAt) : 'recently' }}
              </div>

              <div class="d-flex flex-wrap ga-2" @click.stop>
                <template v-if="suggestion.status === 'PENDING'">
                  <v-btn
                    size="small"
                    color="success"
                    variant="tonal"
                    :loading="store.actionLoadingById[suggestion.id]"
                    @click="handleApprove(suggestion.id)"
                  >
                    Approve
                  </v-btn>
                  <v-btn
                    size="small"
                    color="primary"
                    :loading="store.actionLoadingById[suggestion.id]"
                    @click="handleApproveAndApply(suggestion.id)"
                  >
                    Approve & Apply
                  </v-btn>
                  <v-btn
                    size="small"
                    color="error"
                    variant="tonal"
                    :loading="store.actionLoadingById[suggestion.id]"
                    @click="handleReject(suggestion.id)"
                  >
                    Reject
                  </v-btn>
                  <v-btn size="small" variant="outlined" @click="openEditDialog(suggestion)">Edit</v-btn>
                </template>

                <template v-else-if="suggestion.status === 'APPROVED'">
                  <v-btn
                    size="small"
                    color="primary"
                    :loading="store.actionLoadingById[suggestion.id]"
                    @click="handleApply(suggestion.id)"
                  >
                    Apply
                  </v-btn>
                  <v-btn
                    size="small"
                    color="error"
                    variant="tonal"
                    :loading="store.actionLoadingById[suggestion.id]"
                    @click="handleReject(suggestion.id)"
                  >
                    Reject
                  </v-btn>
                </template>

                <template v-else-if="suggestion.status === 'APPLIED'">
                  <v-btn size="small" variant="outlined" @click="openSuggestionDetail(suggestion, true)">
                    View Result
                  </v-btn>
                </template>
              </div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <v-alert v-else type="info" variant="tonal">
        No suggestions match the current filters.
      </v-alert>

      <div v-if="totalPages > 1" class="d-flex justify-center mt-6">
        <v-pagination v-model="page" :length="totalPages" rounded="circle" />
      </div>
    </template>

    <v-dialog v-model="showDetailDialog" max-width="1100" scrollable>
      <v-card v-if="activeSuggestion">
        <v-card-title class="d-flex align-center ga-3 flex-wrap">
          <div>
            <div class="text-h6">{{ humanize(activeSuggestion.suggestionType) }}</div>
            <div class="text-caption text-medium-emphasis">
              {{ scopeLabel(activeSuggestion) }} · {{ clientNameFor(activeSuggestion.clientId) }}
            </div>
          </div>
          <v-spacer />
          <v-chip :color="riskColor(activeSuggestion.riskLevel)" size="small">{{ activeSuggestion.riskLevel }}</v-chip>
          <v-chip :color="statusColor(activeSuggestion.status)" size="small" variant="tonal">{{ activeSuggestion.status }}</v-chip>
        </v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="12" md="7">
              <v-alert type="info" variant="tonal" class="mb-4">
                {{ activeSuggestion.rationale || 'No rationale provided.' }}
              </v-alert>

              <v-card variant="outlined" class="mb-4">
                <v-card-title class="text-subtitle-1">Payload details</v-card-title>
                <v-card-text>
                  <div v-if="payloadEntries(activeSuggestion).length" class="payload-grid">
                    <div
                      v-for="entry in payloadEntries(activeSuggestion)"
                      :key="entry.key"
                      class="payload-grid-item"
                    >
                      <div class="text-caption text-medium-emphasis">{{ entry.label }}</div>
                      <div class="text-body-2">{{ entry.value }}</div>
                    </div>
                  </div>
                  <pre v-else class="json-preview">{{ formatJson(parsedPayload(activeSuggestion)) }}</pre>
                </v-card-text>
              </v-card>

              <v-card
                v-if="recommendedCreatives(activeSuggestion).length"
                variant="outlined"
                class="mb-4"
              >
                <v-card-title class="text-subtitle-1">Recommended creatives</v-card-title>
                <v-card-text>
                  <v-row>
                    <v-col
                      v-for="creative in recommendedCreatives(activeSuggestion)"
                      :key="creative.asset_id"
                      cols="12"
                      sm="6"
                    >
                      <v-card variant="tonal">
                        <div class="recommended-creative-preview">
                          <img
                            v-if="recommendedCreativeUrls[creative.asset_id]"
                            :src="recommendedCreativeUrls[creative.asset_id]"
                            class="recommended-creative-image"
                          />
                          <v-icon v-else size="40" color="grey">mdi-image</v-icon>
                        </div>
                        <v-card-text>
                          <div class="text-body-2 font-weight-medium">{{ creative.filename || creative.asset_id }}</div>
                          <div class="text-caption text-medium-emphasis">Asset {{ shortId(creative.asset_id) }}</div>
                          <v-chip v-if="creative.quality_score != null" size="x-small" color="success" class="mt-2">
                            Score {{ Number(creative.quality_score).toFixed(0) }}
                          </v-chip>
                        </v-card-text>
                      </v-card>
                    </v-col>
                  </v-row>
                </v-card-text>
              </v-card>

              <v-card variant="outlined">
                <v-card-title class="text-subtitle-1 d-flex align-center">
                  Feedback
                  <v-spacer />
                  <v-progress-circular
                    v-if="store.feedbackSubmittingBySuggestion[activeSuggestion.id]"
                    indeterminate
                    size="18"
                    width="2"
                    color="primary"
                  />
                </v-card-title>
                <v-card-text>
                  <div class="d-flex ga-2 mb-3">
                    <v-btn
                      :variant="feedbackRating === 5 ? 'flat' : 'outlined'"
                      :color="feedbackRating === 5 ? 'success' : undefined"
                      prepend-icon="mdi-thumb-up-outline"
                      @click="feedbackRating = 5"
                    >
                      Helpful
                    </v-btn>
                    <v-btn
                      :variant="feedbackRating === 1 ? 'flat' : 'outlined'"
                      :color="feedbackRating === 1 ? 'error' : undefined"
                      prepend-icon="mdi-thumb-down-outline"
                      @click="feedbackRating = 1"
                    >
                      Not helpful
                    </v-btn>
                  </div>
                  <v-textarea
                    v-model="feedbackComment"
                    label="Comment"
                    variant="outlined"
                    rows="3"
                    auto-grow
                    hide-details
                  />
                  <div class="d-flex justify-end mt-3">
                    <v-btn color="primary" :disabled="feedbackRating == null" @click="submitSuggestionFeedback()">
                      Send feedback
                    </v-btn>
                  </div>
                </v-card-text>
              </v-card>
            </v-col>

            <v-col cols="12" md="5">
              <v-card variant="outlined" class="mb-4">
                <v-card-title class="text-subtitle-1">Meta</v-card-title>
                <v-card-text>
                  <div class="mb-3">
                    <div class="text-caption text-medium-emphasis">Created</div>
                    <div class="text-body-2">{{ formatDateTime(activeSuggestion.createdAt) }}</div>
                  </div>
                  <div class="mb-3">
                    <div class="text-caption text-medium-emphasis">Confidence</div>
                    <v-progress-linear
                      class="mt-2"
                      :model-value="confidencePercent(activeSuggestion.confidence)"
                      color="primary"
                      rounded
                      height="8"
                    />
                    <div class="text-caption mt-1">{{ confidencePercent(activeSuggestion.confidence) }}%</div>
                  </div>
                  <div v-if="activeSuggestion.reviewedAt" class="mb-3">
                    <div class="text-caption text-medium-emphasis">Reviewed</div>
                    <div class="text-body-2">{{ formatDateTime(activeSuggestion.reviewedAt) }}</div>
                  </div>
                </v-card-text>
              </v-card>

              <v-card variant="outlined">
                <v-card-title class="text-subtitle-1 d-flex align-center">
                  Action log
                  <v-spacer />
                  <v-progress-circular
                    v-if="activeSuggestion && store.actionLogsLoadingBySuggestion[activeSuggestion.id]"
                    indeterminate
                    size="18"
                    width="2"
                    color="primary"
                  />
                </v-card-title>
                <v-card-text>
                  <v-expansion-panels
                    v-if="activeSuggestion && (store.actionLogsBySuggestion[activeSuggestion.id] ?? []).length"
                    variant="accordion"
                  >
                    <v-expansion-panel
                      v-for="log in store.actionLogsBySuggestion[activeSuggestion.id]"
                      :key="log.id"
                    >
                      <v-expansion-panel-title>
                        <div class="d-flex align-center ga-2 flex-wrap">
                          <v-chip :color="log.success ? 'success' : 'error'" size="x-small">{{ log.success ? 'SUCCESS' : 'FAILED' }}</v-chip>
                          <span>{{ formatDateTime(log.createdAt) }}</span>
                        </div>
                      </v-expansion-panel-title>
                      <v-expansion-panel-text>
                        <div v-if="log.resultSnapshotJson" class="mb-3">
                          <div class="text-caption text-medium-emphasis mb-1">Before / after snapshot</div>
                          <pre class="json-preview">{{ formatJson(log.resultSnapshotJson) }}</pre>
                        </div>
                        <div v-if="log.metaRequestJson" class="mb-3">
                          <div class="text-caption text-medium-emphasis mb-1">Request</div>
                          <pre class="json-preview">{{ formatJson(log.metaRequestJson) }}</pre>
                        </div>
                        <div v-if="log.metaResponseJson">
                          <div class="text-caption text-medium-emphasis mb-1">Response</div>
                          <pre class="json-preview">{{ formatJson(log.metaResponseJson) }}</pre>
                        </div>
                      </v-expansion-panel-text>
                    </v-expansion-panel>
                  </v-expansion-panels>
                  <v-alert v-else type="info" variant="tonal">
                    No action logs for this suggestion yet.
                  </v-alert>
                </v-card-text>
              </v-card>
            </v-col>
          </v-row>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showDetailDialog = false">Close</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="showEditDialog" max-width="760">
      <v-card>
        <v-card-title>Edit suggestion</v-card-title>
        <v-card-text>
          <v-textarea
            v-model="editPayloadJson"
            label="Payload JSON"
            variant="outlined"
            rows="10"
            auto-grow
          />
          <v-slider
            v-model="editConfidence"
            label="Confidence"
            color="primary"
            thumb-label
            :min="0"
            :max="100"
            :step="1"
            class="mt-4"
          >
            <template #append>
              <span class="text-caption">{{ editConfidence }}%</span>
            </template>
          </v-slider>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showEditDialog = false">Cancel</v-btn>
          <v-btn color="primary" :loading="editingSuggestionId ? store.actionLoadingById[editingSuggestionId] : false" @click="saveSuggestionEdit()">
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar.show" :color="snackbar.color" timeout="3000">
      {{ snackbar.text }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import api from '@/api/client'
import { useClientStore } from '@/stores/clients'
import { useSuggestionStore } from '@/stores/suggestions'
import type { Suggestion } from '@/stores/suggestions'

type SuggestionTypeFilter = 'ALL' | 'DIAGNOSTIC' | 'BUDGET_ADJUST' | 'PAUSE' | 'CREATIVE_TEST' | 'COPY_REFRESH'
type SuggestionStatusFilter = 'PENDING' | 'APPROVED' | 'APPLIED' | 'REJECTED' | null
type DatePreset = '7d' | '14d' | '30d' | 'custom'

interface ScopeEntity {
  id: string
  name: string
}

interface RecommendedCreative {
  asset_id: string
  filename?: string
  quality_score?: number
}

const store = useSuggestionStore()
const clientStore = useClientStore()

const selectedClient = ref<string | null>(null)
const selectedType = ref<SuggestionTypeFilter>('ALL')
const selectedStatus = ref<SuggestionStatusFilter>(null)
const selectedDatePreset = ref<DatePreset>('30d')
const customFrom = ref('')
const customTo = ref('')
const page = ref(1)
const itemsPerPage = 9

const showDetailDialog = ref(false)
const activeSuggestion = ref<Suggestion | null>(null)
const showEditDialog = ref(false)
const editingSuggestionId = ref<string | null>(null)
const editPayloadJson = ref('')
const editConfidence = ref(0)
const feedbackRating = ref<number | null>(null)
const feedbackComment = ref('')
const recommendedCreativeUrls = reactive<Record<string, string>>({})
const scopeMaps = reactive({
  campaigns: {} as Record<string, string>,
  adsets: {} as Record<string, string>,
  ads: {} as Record<string, string>,
})
const snackbar = ref({ show: false, text: '', color: 'success' })

const typeOptions: Array<{ label: string; value: SuggestionTypeFilter }> = [
  { label: 'All', value: 'ALL' },
  { label: 'Diagnostic', value: 'DIAGNOSTIC' },
  { label: 'Budget Adjust', value: 'BUDGET_ADJUST' },
  { label: 'Pause', value: 'PAUSE' },
  { label: 'Creative Test', value: 'CREATIVE_TEST' },
  { label: 'Copy Refresh', value: 'COPY_REFRESH' },
]

const statusOptions: Array<{ label: string; value: SuggestionStatusFilter }> = [
  { label: 'All', value: null },
  { label: 'Pending', value: 'PENDING' },
  { label: 'Approved', value: 'APPROVED' },
  { label: 'Applied', value: 'APPLIED' },
  { label: 'Rejected', value: 'REJECTED' },
]

const dateRangeOptions: Array<{ label: string; value: DatePreset }> = [
  { label: 'Last 7d', value: '7d' },
  { label: 'Last 14d', value: '14d' },
  { label: 'Last 30d', value: '30d' },
  { label: 'Custom', value: 'custom' },
]

const relativeTimeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })

const baseFilteredSuggestions = computed(() => {
  return [...store.suggestions]
    .filter((suggestion) => selectedType.value === 'ALL' || suggestion.suggestionType === selectedType.value)
    .filter((suggestion) => matchesDateRange(suggestion.createdAt))
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
})

const filteredSuggestions = computed(() => {
  return baseFilteredSuggestions.value.filter((suggestion) => !selectedStatus.value || suggestion.status === selectedStatus.value)
})

const totalPages = computed(() => Math.max(1, Math.ceil(filteredSuggestions.value.length / itemsPerPage)))

const pagedSuggestions = computed(() => {
  const start = (page.value - 1) * itemsPerPage
  return filteredSuggestions.value.slice(start, start + itemsPerPage)
})

const summaryStatusItems = computed(() => {
  const counts = {
    PENDING: 0,
    APPROVED: 0,
    APPLIED: 0,
    REJECTED: 0,
  }

  for (const suggestion of baseFilteredSuggestions.value) {
    if (suggestion.status in counts) {
      counts[suggestion.status as keyof typeof counts] += 1
    }
  }

  return [
    { status: 'PENDING' as const, label: 'Pending', count: counts.PENDING },
    { status: 'APPROVED' as const, label: 'Approved', count: counts.APPROVED },
    { status: 'APPLIED' as const, label: 'Applied', count: counts.APPLIED },
    { status: 'REJECTED' as const, label: 'Rejected', count: counts.REJECTED },
  ]
})

watch([selectedType, selectedStatus, selectedDatePreset, customFrom, customTo], () => {
  page.value = 1
})

watch(totalPages, (next) => {
  if (page.value > next) {
    page.value = next
  }
})

function humanize(value: string) {
  return value.replace(/_/g, ' ')
}

function shortId(value: string) {
  return value ? `${value.slice(0, 8)}…` : '—'
}

function confidencePercent(value: number) {
  const normalized = Number(value ?? 0)
  return Math.round(normalized <= 1 ? normalized * 100 : normalized)
}

function typeIcon(type: string) {
  const map: Record<string, string> = {
    DIAGNOSTIC: 'mdi-stethoscope',
    BUDGET_ADJUST: 'mdi-currency-usd',
    PAUSE: 'mdi-pause-circle-outline',
    CREATIVE_TEST: 'mdi-image-outline',
    COPY_REFRESH: 'mdi-text-box-edit-outline',
  }
  return map[type] || 'mdi-lightbulb-outline'
}

function typeColor(type: string) {
  const map: Record<string, string> = {
    ALL: 'primary',
    DIAGNOSTIC: 'info',
    BUDGET_ADJUST: 'orange',
    PAUSE: 'error',
    CREATIVE_TEST: 'success',
    COPY_REFRESH: 'purple',
  }
  return map[type] || 'grey'
}

function typeHexColor(type: string) {
  const map: Record<string, string> = {
    DIAGNOSTIC: '#1E88E5',
    BUDGET_ADJUST: '#FB8C00',
    PAUSE: '#E53935',
    CREATIVE_TEST: '#43A047',
    COPY_REFRESH: '#8E24AA',
  }
  return map[type] || '#90A4AE'
}

function riskColor(level: string) {
  const map: Record<string, string> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'error' }
  return map[level] || 'grey'
}

function statusColor(status: string | null) {
  const map: Record<string, string> = {
    PENDING: 'info',
    APPROVED: 'success',
    APPLIED: 'primary',
    REJECTED: 'grey',
  }
  return status ? (map[status] || 'grey') : 'primary'
}

function clientNameFor(clientId: string) {
  return clientStore.clients.find((client) => client.id === clientId)?.name || 'Unknown client'
}

function formatDateTime(value: string | null) {
  return value ? new Date(value).toLocaleString() : '—'
}

function formatRelativeTime(value: string) {
  const date = new Date(value)
  const diffMs = date.getTime() - Date.now()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (Math.abs(diffMs) < hour) {
    return relativeTimeFormatter.format(Math.round(diffMs / minute), 'minute')
  }
  if (Math.abs(diffMs) < day) {
    return relativeTimeFormatter.format(Math.round(diffMs / hour), 'hour')
  }
  return relativeTimeFormatter.format(Math.round(diffMs / day), 'day')
}

function parseJson(value: unknown) {
  if (value == null) return null
  if (typeof value === 'object') return value
  try {
    return JSON.parse(String(value))
  } catch {
    return value
  }
}

function formatJson(value: unknown) {
  try {
    return JSON.stringify(parseJson(value), null, 2)
  } catch {
    return String(value)
  }
}

function formatValue(value: unknown): string {
  if (value == null || value === '') return '—'
  if (typeof value === 'number') return Number.isInteger(value) ? String(value) : value.toFixed(2)
  if (typeof value === 'boolean') return value ? 'Yes' : 'No'
  if (Array.isArray(value)) return value.map((item) => formatValue(item)).join(', ')
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function formatFieldLabel(key: string) {
  return key.replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase())
}

function parsedPayload(suggestion: Suggestion) {
  const payload = parseJson(suggestion.payloadJson)
  return payload && typeof payload === 'object' ? payload as Record<string, any> : {}
}

function payloadEntries(suggestion: Suggestion) {
  return Object.entries(parsedPayload(suggestion))
    .filter(([key]) => key !== 'recommended_creatives')
    .map(([key, value]) => ({ key, label: formatFieldLabel(key), value: formatValue(value) }))
}

function recommendedCreatives(suggestion: Suggestion): RecommendedCreative[] {
  const payload = parsedPayload(suggestion)
  return Array.isArray(payload.recommended_creatives) ? payload.recommended_creatives : []
}

function payloadSummary(suggestion: Suggestion) {
  const payload = parsedPayload(suggestion)
  if (suggestion.suggestionType === 'BUDGET_ADJUST') {
    const current = payload.current_daily_budget ?? payload.currentBudget ?? payload.current_budget
    const proposed = payload.proposed_daily_budget ?? payload.proposedBudget ?? payload.proposed_budget
    const delta = payload.change_percent ?? payload.changePercent
    if (current != null && proposed != null) {
      const suffix = delta != null ? ` (${Number(delta) > 0 ? '+' : ''}${delta}%)` : ''
      return `Current: ${formatValue(current)} → Proposed: ${formatValue(proposed)}${suffix}`
    }
  }
  if (suggestion.suggestionType === 'PAUSE') {
    return payload.reason ? `Pause recommendation: ${humanize(String(payload.reason))}` : 'Pause this entity based on recent performance.'
  }
  if (suggestion.suggestionType === 'COPY_REFRESH') {
    return 'Copy fatigue detected. Refresh copy to recover engagement.'
  }
  if (suggestion.suggestionType === 'CREATIVE_TEST') {
    return recommendedCreatives(suggestion).length
      ? `Test ${recommendedCreatives(suggestion).length} recommended creatives from the library.`
      : 'Test new creative variants against the current winner.'
  }
  return payload.alert ? humanize(String(payload.alert)) : 'Review the full payload for more detail.'
}

function scopeLabel(suggestion: Suggestion) {
  const id = suggestion.scopeId
  if (suggestion.scopeType === 'CAMPAIGN') {
    return `Campaign: ${scopeMaps.campaigns[id] || shortId(id)}`
  }
  if (suggestion.scopeType === 'ADSET') {
    return `Adset: ${scopeMaps.adsets[id] || shortId(id)}`
  }
  if (suggestion.scopeType === 'AD') {
    return `Ad: ${scopeMaps.ads[id] || shortId(id)}`
  }
  return `${humanize(suggestion.scopeType)}: ${shortId(id)}`
}

function matchesDateRange(value: string) {
  const createdAt = new Date(value)
  if (selectedDatePreset.value === 'custom') {
    if (customFrom.value) {
      const from = new Date(`${customFrom.value}T00:00:00`)
      if (createdAt < from) return false
    }
    if (customTo.value) {
      const to = new Date(`${customTo.value}T23:59:59`)
      if (createdAt > to) return false
    }
    return true
  }

  const days = selectedDatePreset.value === '7d' ? 7 : selectedDatePreset.value === '14d' ? 14 : 30
  const cutoff = new Date()
  cutoff.setDate(cutoff.getDate() - days)
  return createdAt >= cutoff
}

function setTypeFilter(value: SuggestionTypeFilter) {
  selectedType.value = value
}

function setStatusFilter(value: SuggestionStatusFilter) {
  selectedStatus.value = value
}

function setDatePreset(value: DatePreset) {
  selectedDatePreset.value = value
}

async function loadScopeMaps(clientId: string) {
  scopeMaps.campaigns = {}
  scopeMaps.adsets = {}
  scopeMaps.ads = {}

  try {
    const { data: campaigns } = await api.get(`/clients/${clientId}/campaigns`)
    const campaignRows: ScopeEntity[] = Array.isArray(campaigns) ? campaigns : []
    for (const campaign of campaignRows) {
      scopeMaps.campaigns[campaign.id] = campaign.name
    }

    const adsetGroups = await Promise.all(campaignRows.map(async (campaign) => {
      try {
        const response = await api.get(`/campaigns/${campaign.id}/adsets`)
        return Array.isArray(response.data) ? response.data : []
      } catch {
        return []
      }
    }))
    const adsets = adsetGroups.flat() as ScopeEntity[]
    for (const adset of adsets) {
      scopeMaps.adsets[adset.id] = adset.name
    }

    const adGroups = await Promise.all(adsets.map(async (adset) => {
      try {
        const response = await api.get(`/adsets/${adset.id}/ads`)
        return Array.isArray(response.data) ? response.data : []
      } catch {
        return []
      }
    }))
    const ads = adGroups.flat() as ScopeEntity[]
    for (const ad of ads) {
      scopeMaps.ads[ad.id] = ad.name
    }
  } catch {
    // Scope names are optional; fall back to IDs if lookup fails.
  }
}

async function onClientChange(clientId: string | null) {
  if (!clientId) return
  page.value = 1
  await Promise.all([
    store.fetchSuggestions(clientId),
    loadScopeMaps(clientId),
  ])
}

async function handleApprove(suggestionId: string) {
  try {
    await store.approveSuggestion(suggestionId)
    snackbar.value = { show: true, text: 'Suggestion approved', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Approve failed', color: 'error' }
  }
}

async function handleApproveAndApply(suggestionId: string) {
  try {
    const updated = await store.approveAndApplySuggestion(suggestionId)
    snackbar.value = { show: true, text: 'Suggestion approved and applied', color: 'success' }
    if (updated) {
      await openSuggestionDetail(updated, true)
    }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Approve & apply failed', color: 'error' }
  }
}

async function handleReject(suggestionId: string) {
  try {
    await store.rejectSuggestion(suggestionId)
    snackbar.value = { show: true, text: 'Suggestion rejected', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Reject failed', color: 'error' }
  }
}

async function handleApply(suggestionId: string) {
  try {
    const updated = await store.applySuggestion(suggestionId)
    snackbar.value = { show: true, text: 'Suggestion applied', color: 'success' }
    if (updated) {
      await openSuggestionDetail(updated, true)
    }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Apply failed', color: 'error' }
  }
}

function openEditDialog(suggestion: Suggestion) {
  editingSuggestionId.value = suggestion.id
  editPayloadJson.value = formatJson(suggestion.payloadJson)
  editConfidence.value = confidencePercent(suggestion.confidence)
  showEditDialog.value = true
}

async function saveSuggestionEdit() {
  if (!editingSuggestionId.value) return
  try {
    JSON.parse(editPayloadJson.value)
  } catch {
    snackbar.value = { show: true, text: 'Payload JSON is invalid', color: 'error' }
    return
  }

  try {
    await store.updateSuggestion(editingSuggestionId.value, {
      payloadJson: editPayloadJson.value,
      confidence: editConfidence.value / 100,
    })
    showEditDialog.value = false
    snackbar.value = { show: true, text: 'Suggestion updated', color: 'success' }
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Update failed', color: 'error' }
  }
}

async function loadRecommendedCreativeUrls(suggestion: Suggestion) {
  const creatives = recommendedCreatives(suggestion)
  await Promise.allSettled(creatives.map(async (creative) => {
    if (!creative.asset_id || recommendedCreativeUrls[creative.asset_id]) return
    try {
      const response = await api.get(`/creatives/${creative.asset_id}/url`)
      recommendedCreativeUrls[creative.asset_id] = response.data.url
    } catch {
      // Ignore preview failures.
    }
  }))
}

async function openSuggestionDetail(suggestion: Suggestion, focusActionLog = false) {
  activeSuggestion.value = suggestion
  showDetailDialog.value = true
  feedbackRating.value = null
  feedbackComment.value = ''
  await Promise.allSettled([
    store.fetchActionLogs(suggestion.id, focusActionLog || suggestion.status === 'APPLIED'),
    loadRecommendedCreativeUrls(suggestion),
  ])
}

async function submitSuggestionFeedback() {
  if (!selectedClient.value || !activeSuggestion.value || feedbackRating.value == null) return
  try {
    await store.submitFeedback(selectedClient.value, activeSuggestion.value.id, {
      rating: feedbackRating.value,
      comment: feedbackComment.value,
    })
    snackbar.value = { show: true, text: 'Feedback submitted', color: 'success' }
    feedbackRating.value = null
    feedbackComment.value = ''
  } catch (e: any) {
    snackbar.value = { show: true, text: e.response?.data?.message || e.message || 'Feedback failed', color: 'error' }
  }
}

onMounted(async () => {
  await clientStore.fetchClients()
  if (!selectedClient.value && clientStore.clients.length > 0) {
    selectedClient.value = clientStore.clients[0].id
    await onClientChange(selectedClient.value)
  }
})
</script>

<style scoped>
.summary-bar {
  row-gap: 6px;
}

.suggestion-card {
  position: relative;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.suggestion-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.08);
}

.suggestion-card-muted {
  opacity: 0.78;
}

.suggestion-card-bar {
  position: absolute;
  inset: 0 auto 0 0;
  width: 6px;
  border-top-left-radius: inherit;
  border-bottom-left-radius: inherit;
}

.rationale-preview {
  min-height: 66px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.payload-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.payload-grid-item {
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.03);
}

.json-preview {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 0.82rem;
  padding: 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.04);
}

.recommended-creative-preview {
  height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
  overflow: hidden;
}

.recommended-creative-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
