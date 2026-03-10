<template>
  <v-container fluid>
    <h1 class="text-h4 mb-2">Platform Dashboard</h1>
    <p class="text-subtitle-1 text-medium-emphasis mb-6">
      Overview of all agencies on the platform.
    </p>

    <!-- KPI cards -->
    <v-row class="mb-6">
      <v-col cols="12" sm="6" md="3">
        <v-card color="primary" variant="tonal">
          <v-card-text class="text-center">
            <div class="text-h4">{{ agencies.length }}</div>
            <div class="text-caption">Total Agencies</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card color="info" variant="tonal">
          <v-card-text class="text-center">
            <div class="text-h4">{{ totalUsers }}</div>
            <div class="text-caption">Total Users</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card color="success" variant="tonal">
          <v-card-text class="text-center">
            <div class="text-h4">{{ activeAgencies }}</div>
            <div class="text-caption">Active Agencies</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" sm="6" md="3">
        <v-card color="warning" variant="tonal">
          <v-card-text class="text-center">
            <div class="text-h4">{{ dashboard?.totalClients ?? 0 }}</div>
            <div class="text-caption">Total Clients</div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-alert v-if="error" type="error" closable class="mb-4" @click:close="error = null">
      {{ error }}
    </v-alert>

    <!-- Agency table -->
    <v-card>
      <v-card-title class="d-flex align-center">
        <span>Agencies</span>
        <v-spacer />
        <v-btn color="primary" prepend-icon="mdi-plus" @click="showCreate = true">
          Create Agency
        </v-btn>
      </v-card-title>

      <v-data-table
        :headers="headers"
        :items="agencies"
        :loading="loading"
        item-value="id"
        hover
        @click:row="(_e: Event, { item }: any) => goToAgency(item.id)"
        class="cursor-pointer"
      >
        <template v-slot:[`item.status`]="{ item }">
          <v-chip :color="item.status === 'ACTIVE' ? 'success' : 'error'" size="small" label>
            {{ item.status }}
          </v-chip>
        </template>
        <template v-slot:[`item.planCode`]="{ item }">
          <v-chip size="small" variant="outlined">{{ item.planCode || 'DEFAULT' }}</v-chip>
        </template>
        <template v-slot:[`item.userCount`]="{ item }">
          {{ item.userCount ?? '—' }}
        </template>
        <template v-slot:[`item.createdAt`]="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>
      </v-data-table>
    </v-card>

    <!-- Create Agency Dialog -->
    <v-dialog v-model="showCreate" max-width="480" persistent>
      <v-card>
        <v-card-title>Create Agency</v-card-title>
        <v-card-text>
          <v-form v-model="createValid">
            <v-text-field
              v-model="createForm.name"
              label="Agency Name"
              :rules="[rules.required]"
              class="mb-2"
            />
            <v-text-field
              v-model="createForm.planCode"
              label="Plan Code"
              hint="Optional plan identifier"
              persistent-hint
            />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="closeCreate">Cancel</v-btn>
          <v-btn color="primary" :disabled="!createValid" :loading="creating" @click="handleCreate">
            Create
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Agency Intelligence Section -->
    <v-divider class="my-6" />
    <div class="d-flex align-center mb-4">
      <v-icon color="deep-purple" size="28" class="mr-2">mdi-brain</v-icon>
      <h2 class="text-h5 font-weight-bold">Agency Intelligence</h2>
      <v-spacer />
      <v-btn
        color="deep-purple"
        variant="tonal"
        :loading="intelLoading"
        @click="fetchIntelligence"
        prepend-icon="mdi-refresh"
      >
        {{ intelReport ? 'Refresh' : 'Generate' }}
      </v-btn>
    </div>

    <v-alert v-if="intelError" type="error" variant="tonal" class="mb-4" closable @click:close="intelError = null">
      {{ intelError }}
    </v-alert>

    <v-progress-linear v-if="intelLoading" indeterminate color="deep-purple" class="mb-4" />

    <template v-if="intelReport">
      <!-- AI Narrative -->
      <v-card class="mb-4" color="deep-purple-lighten-5" variant="flat">
        <v-card-text>
          <div class="d-flex align-center mb-2">
            <v-icon color="deep-purple" class="mr-2">mdi-robot-outline</v-icon>
            <span class="text-subtitle-2 font-weight-bold">AI Executive Briefing</span>
          </div>
          <div class="text-body-2" style="white-space: pre-wrap">{{ intelReport.aiNarrative }}</div>
        </v-card-text>
      </v-card>

      <!-- Industry Benchmarks -->
      <v-card class="mb-4" v-if="intelReport.benchmarks.length > 0">
        <v-card-title class="text-subtitle-1">
          <v-icon class="mr-2">mdi-chart-bar</v-icon>
          Industry Benchmarks (30 days)
        </v-card-title>
        <v-data-table
          :headers="benchmarkHeaders"
          :items="intelReport.benchmarks"
          density="compact"
          no-data-text="No benchmark data"
        >
          <template #item.avgCtr="{ item }">{{ item.avgCtr.toFixed(2) }}%</template>
          <template #item.avgCpc="{ item }">${{ Number(item.avgCpc).toFixed(2) }}</template>
          <template #item.avgRoas="{ item }">{{ Number(item.avgRoas).toFixed(2) }}x</template>
          <template #item.totalSpend="{ item }">${{ Number(item.totalSpend).toLocaleString() }}</template>
        </v-data-table>
      </v-card>

      <!-- Agency Health Scores -->
      <v-card class="mb-4" v-if="intelReport.agencyScores.length > 0">
        <v-card-title class="text-subtitle-1">
          <v-icon class="mr-2">mdi-heart-pulse</v-icon>
          Agency Health Scores
        </v-card-title>
        <v-data-table
          :headers="healthHeaders"
          :items="intelReport.agencyScores"
          density="compact"
          no-data-text="No agency data"
        >
          <template #item.healthScore="{ item }">
            <v-chip
              :color="item.healthScore >= 70 ? 'success' : item.healthScore >= 40 ? 'warning' : 'error'"
              size="small"
              variant="flat"
            >
              {{ item.healthScore }}
            </v-chip>
          </template>
          <template #item.activeClientPct="{ item }">{{ item.activeClientPct }}%</template>
          <template #item.avgRoas="{ item }">{{ Number(item.avgRoas).toFixed(2) }}x</template>
          <template #item.syncFreshnessPct="{ item }">{{ item.syncFreshnessPct }}%</template>
          <template #item.suggestionAdoptionPct="{ item }">{{ item.suggestionAdoptionPct }}%</template>
          <template #item.status="{ item }">
            <v-chip :color="item.status === 'ACTIVE' ? 'success' : 'grey'" size="small" label>
              {{ item.status }}
            </v-chip>
          </template>
        </v-data-table>
      </v-card>

      <!-- Churn Risks -->
      <v-card v-if="intelReport.churnRisks.length > 0">
        <v-card-title class="text-subtitle-1">
          <v-icon color="error" class="mr-2">mdi-alert-circle-outline</v-icon>
          Churn Risk Signals ({{ intelReport.churnRisks.length }} clients)
        </v-card-title>
        <v-list density="compact">
          <v-list-item v-for="risk in intelReport.churnRisks" :key="risk.clientId">
            <template #prepend>
              <v-chip
                :color="risk.riskLevel === 'HIGH' ? 'error' : risk.riskLevel === 'MEDIUM' ? 'warning' : 'info'"
                size="small"
                variant="flat"
                class="mr-2"
              >
                {{ risk.riskLevel }}
              </v-chip>
            </template>
            <v-list-item-title class="text-body-2">
              {{ risk.clientName }}
              <span v-if="risk.industry" class="text-medium-emphasis"> · {{ risk.industry }}</span>
            </v-list-item-title>
            <v-list-item-subtitle class="text-caption">
              {{ risk.signals.join(' · ') }}
            </v-list-item-subtitle>
          </v-list-item>
        </v-list>
      </v-card>

      <v-alert v-if="intelReport.churnRisks.length === 0" type="success" variant="tonal" class="mt-2">
        No clients currently flagged for churn risk.
      </v-alert>
    </template>

    <v-snackbar v-model="snackbar" color="success" :timeout="3000">{{ snackbarText }}</v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ownerApi } from '@/api/owner'

interface Agency {
  id: string
  name: string
  status: string
  planCode: string | null
  userCount?: number
  createdAt: string
}

interface Dashboard {
  totalAgencies: number
  totalUsers: number
  totalClients: number
}

const router = useRouter()

const agencies = ref<Agency[]>([])
const dashboard = ref<Dashboard | null>(null)
const loading = ref(false)
const error = ref<string | null>(null)

const headers = [
  { title: 'Name', key: 'name' },
  { title: 'Status', key: 'status' },
  { title: 'Plan', key: 'planCode' },
  { title: 'Users', key: 'userCount' },
  { title: 'Created', key: 'createdAt' },
]

const totalUsers = computed(() => dashboard.value?.totalUsers ?? 0)
const activeAgencies = computed(() =>
  agencies.value.filter(a => a.status === 'ACTIVE').length
)

/* ── Create Agency ── */
const showCreate = ref(false)
const createValid = ref(false)
const creating = ref(false)
const createForm = ref({ name: '', planCode: '' })

function closeCreate() {
  showCreate.value = false
  createForm.value = { name: '', planCode: '' }
}

async function handleCreate() {
  creating.value = true
  try {
    const payload: { name: string; planCode?: string } = { name: createForm.value.name }
    if (createForm.value.planCode) payload.planCode = createForm.value.planCode
    await ownerApi.createAgency(payload)
    showSnack('Agency created')
    closeCreate()
    await fetchAll()
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to create agency'
  } finally {
    creating.value = false
  }
}

/* ── Navigation ── */
function goToAgency(id: string) {
  router.push({ name: 'owner-agency-detail', params: { id } })
}

/* ── Helpers ── */
function formatDate(iso: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString()
}

const snackbar = ref(false)
const snackbarText = ref('')
function showSnack(msg: string) {
  snackbarText.value = msg
  snackbar.value = true
}

const rules = {
  required: (v: string) => !!v || 'Required',
}

async function fetchAll() {
  loading.value = true
  error.value = null
  try {
    const [agRes, dashRes] = await Promise.all([
      ownerApi.listAgencies(),
      ownerApi.getDashboard().catch(() => ({ data: null })),
    ])
    agencies.value = agRes.data
    dashboard.value = dashRes.data
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load data'
  } finally {
    loading.value = false
  }
}

/* ── Agency Intelligence ── */

interface IndustryBenchmark {
  industry: string
  totalClients: number
  clientsWithData: number
  avgCtr: number
  avgCpc: number
  avgRoas: number
  totalSpend: number
}

interface AgencyScore {
  agencyId: string
  agencyName: string
  status: string
  totalClients: number
  activeClientPct: number
  avgRoas: number
  syncFreshnessPct: number
  suggestionAdoptionPct: number
  healthScore: number
}

interface ChurnRiskItem {
  clientId: string
  agencyId: string
  clientName: string
  industry: string | null
  riskLevel: string
  signals: string[]
}

interface IntelligenceReport {
  benchmarks: IndustryBenchmark[]
  agencyScores: AgencyScore[]
  churnRisks: ChurnRiskItem[]
  aiNarrative: string
}

const intelReport = ref<IntelligenceReport | null>(null)
const intelLoading = ref(false)
const intelError = ref<string | null>(null)

const benchmarkHeaders = [
  { title: 'Industry', key: 'industry' },
  { title: 'Clients', key: 'clientsWithData', align: 'end' as const },
  { title: 'Avg CTR', key: 'avgCtr', align: 'end' as const },
  { title: 'Avg CPC', key: 'avgCpc', align: 'end' as const },
  { title: 'Avg ROAS', key: 'avgRoas', align: 'end' as const },
  { title: 'Total Spend', key: 'totalSpend', align: 'end' as const },
]

const healthHeaders = [
  { title: 'Agency', key: 'agencyName' },
  { title: 'Status', key: 'status' },
  { title: 'Health', key: 'healthScore', align: 'center' as const },
  { title: 'Clients', key: 'totalClients', align: 'end' as const },
  { title: 'Active %', key: 'activeClientPct', align: 'end' as const },
  { title: 'Avg ROAS', key: 'avgRoas', align: 'end' as const },
  { title: 'Sync Fresh', key: 'syncFreshnessPct', align: 'end' as const },
  { title: 'Adoption', key: 'suggestionAdoptionPct', align: 'end' as const },
]

async function fetchIntelligence() {
  intelLoading.value = true
  intelError.value = null
  try {
    const res = await ownerApi.getIntelligence()
    intelReport.value = res.data
  } catch (e: any) {
    intelError.value = e.response?.data?.message || 'Failed to generate intelligence report'
  } finally {
    intelLoading.value = false
  }
}

onMounted(fetchAll)
</script>

<style scoped>
.cursor-pointer :deep(tbody tr) {
  cursor: pointer;
}
</style>
