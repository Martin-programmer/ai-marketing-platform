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

onMounted(fetchAll)
</script>

<style scoped>
.cursor-pointer :deep(tbody tr) {
  cursor: pointer;
}
</style>
