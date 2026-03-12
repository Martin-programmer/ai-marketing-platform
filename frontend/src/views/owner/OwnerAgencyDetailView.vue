<template>
  <v-container fluid>
    <!-- Back button + title -->
    <div class="d-flex align-center mb-4">
      <v-btn icon="mdi-arrow-left" variant="text" @click="router.push({ name: 'owner-dashboard' })" />
      <h1 class="text-h4 ml-2">{{ agency?.name || 'Agency' }}</h1>
      <v-spacer />
      <v-btn variant="outlined" prepend-icon="mdi-pencil" @click="showEdit = true">
        Edit Agency
      </v-btn>
    </div>

    <v-alert v-if="error" type="error" closable class="mb-4" @click:close="error = null">
      {{ error }}
    </v-alert>

    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

    <!-- Agency info card -->
    <v-card class="mb-6" v-if="agency">
      <v-card-text>
        <v-row>
          <v-col cols="12" md="4">
            <div class="text-caption text-medium-emphasis">Status</div>
            <v-chip :color="agency.status === 'ACTIVE' ? 'success' : 'error'" size="small" label>
              {{ agency.status }}
            </v-chip>
          </v-col>
          <v-col cols="12" md="4">
            <div class="text-caption text-medium-emphasis">Plan</div>
            <div>{{ agency.planCode || 'DEFAULT' }}</div>
          </v-col>
          <v-col cols="12" md="4">
            <div class="text-caption text-medium-emphasis">Created</div>
            <div>{{ formatDate(agency.createdAt) }}</div>
          </v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <!-- Users in this agency -->
    <v-card>
      <v-card-title class="d-flex align-center">
        <span>Users ({{ users.length }})</span>
        <v-spacer />
        <v-btn color="primary" size="small" prepend-icon="mdi-account-plus" @click="showInvite = true">
          Invite User
        </v-btn>
      </v-card-title>

      <v-data-table
        :headers="userHeaders"
        :items="users"
        :loading="usersLoading"
        item-value="id"
      >
        <template v-slot:[`item.role`]="{ item }">
          <v-chip :color="roleColor(item.role)" size="small" label>
            {{ item.role }}
          </v-chip>
        </template>
        <template v-slot:[`item.status`]="{ item }">
          <v-chip :color="statusColor(item.status)" size="small">
            {{ item.status }}
          </v-chip>
        </template>
        <template v-slot:[`item.createdAt`]="{ item }">
          {{ formatDate(item.createdAt) }}
        </template>
      </v-data-table>
    </v-card>

    <!-- Edit Agency Dialog -->
    <v-dialog v-model="showEdit" max-width="480" persistent>
      <v-card>
        <v-card-title>Edit Agency</v-card-title>
        <v-card-text>
          <v-form v-model="editValid">
            <v-text-field
              v-model="editForm.name"
              label="Agency Name"
              :rules="[rules.required]"
              class="mb-2"
            />
            <v-text-field
              v-model="editForm.planCode"
              label="Plan Code"
              class="mb-2"
            />
            <v-select
              v-model="editForm.status"
              label="Status"
              :items="['ACTIVE', 'SUSPENDED']"
            />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="showEdit = false">Cancel</v-btn>
          <v-btn color="primary" :disabled="!editValid" :loading="saving" @click="handleSave">
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Invite User Dialog -->
    <v-dialog v-model="showInvite" max-width="520" persistent>
      <v-card>
        <v-card-title>Invite User to {{ agency?.name }}</v-card-title>
        <v-card-text>
          <v-form v-model="inviteValid">
            <v-text-field
              v-model="inviteForm.email"
              label="Email"
              type="email"
              :rules="[rules.required, rules.email]"
              class="mb-2"
            />
            <v-text-field
              v-model="inviteForm.displayName"
              label="Display Name"
              class="mb-2"
            />
            <v-select
              v-model="inviteForm.role"
              label="Role"
              :items="roleOptions"
              :rules="[rules.required]"
            />
            <v-select
              v-if="inviteForm.role === 'CLIENT_USER'"
              v-model="inviteForm.clientId"
              label="Client"
              :items="clients"
              item-title="name"
              item-value="id"
              :loading="clientsLoading"
              :rules="[rules.required]"
              hint="Select the client this portal user should access"
            />
            <v-alert v-if="inviteForm.email" type="info" variant="tonal" class="mt-3">
              Invitation email will be sent to {{ inviteForm.email }}.
            </v-alert>
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="closeInvite">Cancel</v-btn>
          <v-btn color="primary" :disabled="!inviteValid" :loading="inviting" @click="handleInvite">
            Invite
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-snackbar v-model="snackbar" color="success" :timeout="3000">{{ snackbarText }}</v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/client'
import { ownerApi } from '@/api/owner'

interface Agency {
  id: string
  name: string
  status: string
  planCode: string | null
  createdAt: string
}

interface AgencyUser {
  id: string
  email: string
  displayName: string
  role: string
  status: string
  createdAt: string
}

interface ClientOption {
  id: string
  name: string
}

const route = useRoute()
const router = useRouter()
const agencyId = route.params.id as string

const agency = ref<Agency | null>(null)
const users = ref<AgencyUser[]>([])
const loading = ref(false)
const usersLoading = ref(false)
const error = ref<string | null>(null)

const userHeaders = [
  { title: 'Name', key: 'displayName' },
  { title: 'Email', key: 'email' },
  { title: 'Role', key: 'role' },
  { title: 'Status', key: 'status' },
  { title: 'Created', key: 'createdAt' },
]
const roleOptions = ['AGENCY_ADMIN', 'AGENCY_USER', 'CLIENT_USER']
const clients = ref<ClientOption[]>([])
const clientsLoading = ref(false)

/* ── Edit Agency ── */
const showEdit = ref(false)
const editValid = ref(false)
const saving = ref(false)
const editForm = ref({ name: '', planCode: '', status: '' })

function openEditFromAgency() {
  if (!agency.value) return
  editForm.value = {
    name: agency.value.name,
    planCode: agency.value.planCode || '',
    status: agency.value.status,
  }
}

async function handleSave() {
  saving.value = true
  try {
    const { data } = await ownerApi.updateAgency(agencyId, editForm.value)
    agency.value = data
    showEdit.value = false
    showSnack('Agency updated')
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to update'
  } finally {
    saving.value = false
  }
}

/* ── Invite User ── */
const showInvite = ref(false)
const inviteValid = ref(false)
const inviting = ref(false)
const inviteForm = ref({ email: '', displayName: '', role: 'AGENCY_ADMIN', clientId: '' })

function closeInvite() {
  showInvite.value = false
  inviteForm.value = { email: '', displayName: '', role: 'AGENCY_ADMIN', clientId: '' }
}

async function handleInvite() {
  inviting.value = true
  try {
    const payload = inviteForm.value.role === 'CLIENT_USER'
      ? { ...inviteForm.value }
      : {
          email: inviteForm.value.email,
          displayName: inviteForm.value.displayName,
          role: inviteForm.value.role,
        }
    await ownerApi.inviteAgencyUser(agencyId, payload)
    showSnack(`Invitation sent to ${inviteForm.value.email}`)
    closeInvite()
    await fetchUsers()
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to invite user'
  } finally {
    inviting.value = false
  }
}

/* ── Helpers ── */
function roleColor(role: string) {
  switch (role) {
    case 'AGENCY_ADMIN': return 'primary'
    case 'AGENCY_USER': return 'info'
    case 'CLIENT_USER': return 'warning'
    case 'OWNER_ADMIN': return 'deep-purple'
    default: return 'grey'
  }
}

function statusColor(status: string) {
  switch (status) {
    case 'ACTIVE': return 'success'
    case 'INVITED': return 'orange'
    default: return 'grey'
  }
}

function formatDate(iso: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString()
}

const snackbar = ref(false)
const snackbarText = ref('')
function showSnack(msg: string) { snackbarText.value = msg; snackbar.value = true }

const rules = {
  required: (v: string) => !!v || 'Required',
  email: (v: string) => /.+@.+\..+/.test(v) || 'Invalid email',
}

async function fetchClients() {
  clientsLoading.value = true
  try {
    const { data } = await api.get('/clients', { params: { agencyId } })
    clients.value = ((data || []) as ClientOption[]).map((client) => ({ id: client.id, name: client.name }))
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load clients'
  } finally {
    clientsLoading.value = false
  }
}

async function fetchAgency() {
  loading.value = true
  try {
    const { data } = await ownerApi.listAgencies()
    const found = (data as Agency[]).find(a => a.id === agencyId)
    if (found) {
      agency.value = found
      openEditFromAgency()
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load agency'
  } finally {
    loading.value = false
  }
}

async function fetchUsers() {
  usersLoading.value = true
  try {
    const { data } = await ownerApi.listAgencyUsers(agencyId)
    users.value = data
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to load users'
  } finally {
    usersLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([fetchAgency(), fetchUsers(), fetchClients()])
})
</script>
