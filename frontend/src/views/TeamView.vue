<template>
  <v-container fluid>
    <v-row>
      <v-col>
        <h1 class="text-h4 mb-4">Team Management</h1>
        <p class="text-subtitle-1 mb-6 text-medium-emphasis">
          Manage your agency's team members, invite new users, and configure roles.
        </p>
      </v-col>
      <v-col v-if="isOwnerAdmin" cols="12" md="4" class="d-flex align-center">
        <v-select
          v-model="selectedAgencyId"
          label="Agency"
          :items="agencyOptions"
          item-title="name"
          item-value="id"
          :loading="agenciesLoading"
          :rules="[rules.required]"
          clearable
          class="w-100"
          @update:model-value="onAgencyChange"
        />
      </v-col>
      <v-col cols="auto" class="d-flex align-center">
        <v-btn color="primary" prepend-icon="mdi-account-plus" :disabled="isOwnerAdmin && !selectedAgencyId" @click="showInviteDialog = true">
          Invite User
        </v-btn>
      </v-col>
    </v-row>

    <v-alert
      v-if="isOwnerAdmin && !selectedAgencyId"
      type="info"
      variant="tonal"
      class="mb-4"
    >
      Select an agency to load and manage its team.
    </v-alert>

    <v-alert v-if="teamStore.error" type="error" closable class="mb-4" @click:close="teamStore.error = null">
      {{ teamStore.error }}
    </v-alert>

    <v-data-table
      :headers="headers"
      :items="teamStore.users"
      :loading="teamStore.loading"
      class="elevation-1"
      item-value="id"
      :no-data-text="isOwnerAdmin && !selectedAgencyId ? 'Select an agency first' : 'No users found'"
    >
      <template v-slot:[`item.displayName`]="{ item }">
        <div>
          <strong>{{ item.displayName }}</strong>
        </div>
      </template>

      <template v-slot:[`item.role`]="{ item }">
        <v-chip :color="roleColor(item.role)" size="small" label>
          {{ item.role }}
        </v-chip>
      </template>

      <template v-slot:[`item.status`]="{ item }">
        <v-chip :color="item.status === 'ACTIVE' ? 'success' : 'error'" size="small">
          {{ item.status }}
        </v-chip>
      </template>

      <template v-slot:[`item.createdAt`]="{ item }">
        {{ formatDate(item.createdAt) }}
      </template>

      <template v-slot:[`item.actions`]="{ item }">
        <v-tooltip v-if="item.role === 'AGENCY_USER'" text="Manage Permissions" location="top">
          <template v-slot:activator="{ props: tp }">
            <v-btn
              v-bind="tp"
              icon="mdi-shield-key"
              size="small"
              variant="text"
              color="purple"
              @click="openPermissions(item)"
            />
          </template>
        </v-tooltip>
        <v-chip
          v-if="item.role === 'AGENCY_ADMIN'"
          size="x-small"
          color="primary"
          variant="tonal"
          class="mr-1"
        >
          Full Access
        </v-chip>
        <v-btn icon="mdi-pencil" size="small" variant="text" @click="openEditDialog(item)" />
        <v-btn
          v-if="item.status === 'ACTIVE'"
          icon="mdi-account-off"
          size="small"
          variant="text"
          color="error"
          @click="handleDisable(item)"
        />
        <v-btn
          v-else
          icon="mdi-account-check"
          size="small"
          variant="text"
          color="success"
          @click="handleEnable(item)"
        />
      </template>
    </v-data-table>

    <!-- Invite User Dialog -->
    <v-dialog v-model="showInviteDialog" max-width="520" persistent>
      <v-card>
        <v-card-title>Invite User</v-card-title>
        <v-card-text>
          <v-form ref="inviteFormRef" v-model="inviteFormValid">
            <v-text-field
              v-model="inviteForm.email"
              label="Email"
              type="email"
              :rules="[rules.required, rules.email]"
              class="mb-2"
            />
            <v-text-field
              v-model="inviteForm.password"
              label="Password"
              type="password"
              :rules="[rules.required, rules.minLength(6)]"
              class="mb-2"
            />
            <v-text-field
              v-model="inviteForm.displayName"
              label="Display Name"
              :rules="[rules.required]"
              class="mb-2"
            />
            <v-select
              v-model="inviteForm.role"
              label="Role"
              :items="roleOptions"
              :rules="[rules.required]"
              class="mb-2"
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
              hint="Select the client this user belongs to"
            />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="closeInviteDialog">Cancel</v-btn>
          <v-btn color="primary" :disabled="!inviteFormValid" :loading="teamStore.loading" @click="handleInvite">
            Invite
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Edit User Dialog -->
    <v-dialog v-model="showEditDialog" max-width="480" persistent>
      <v-card>
        <v-card-title>Edit User</v-card-title>
        <v-card-text>
          <v-form v-model="editFormValid">
            <v-text-field
              v-model="editForm.displayName"
              label="Display Name"
              :rules="[rules.required]"
              class="mb-2"
            />
            <v-select
              v-model="editForm.role"
              label="Role"
              :items="roleOptions"
              :rules="[rules.required]"
              class="mb-2"
            />
            <v-select
              v-model="editForm.status"
              label="Status"
              :items="statusOptions"
              class="mb-2"
            />
          </v-form>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="showEditDialog = false">Cancel</v-btn>
          <v-btn color="primary" :disabled="!editFormValid" @click="handleUpdate">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Permission Management Dialog -->
    <PermissionManagementDialog
      v-model="showPermDialog"
      :user-id="permDialogUserId"
      :user-name="permDialogUserName"
      @saved="showSnackbar('Permissions saved')"
    />

    <!-- Success Snackbar -->
    <v-snackbar v-model="snackbar" color="success" :timeout="3000">
      {{ snackbarText }}
    </v-snackbar>
  </v-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import api from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useTeamStore } from '@/stores/team'
import PermissionManagementDialog from '@/components/PermissionManagementDialog.vue'

const authStore = useAuthStore()
const teamStore = useTeamStore()

const headers = [
  { title: 'Name', key: 'displayName' },
  { title: 'Email', key: 'email' },
  { title: 'Role', key: 'role' },
  { title: 'Status', key: 'status' },
  { title: 'Created', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const }
]

const roleOptions = ['AGENCY_ADMIN', 'AGENCY_USER', 'CLIENT_USER']
const statusOptions = ['ACTIVE', 'DISABLED']

const clients = ref<Array<{ id: string; name: string }>>([])
const clientsLoading = ref(false)
const agencyOptions = ref<Array<{ id: string; name: string }>>([])
const agenciesLoading = ref(false)
const selectedAgencyId = ref<string | null>(null)

const isOwnerAdmin = computed(() => authStore.userRole === 'OWNER_ADMIN')

interface TeamUser {
  id: string
  email: string
  displayName: string
  role: string
  status: string
  createdAt: string
}

interface InvitePayload {
  email: string
  password: string
  displayName: string
  role: string
  clientId?: string
}

/* ── Invite ── */
const showInviteDialog = ref(false)
const inviteFormRef = ref()
const inviteFormValid = ref(false)
const inviteForm = ref({
  email: '',
  password: '',
  displayName: '',
  role: 'AGENCY_USER',
  clientId: ''
})

function closeInviteDialog() {
  showInviteDialog.value = false
  inviteForm.value = { email: '', password: '', displayName: '', role: 'AGENCY_USER', clientId: '' }
}

async function handleInvite() {
  const payload: InvitePayload = { ...inviteForm.value }
  if (payload.role !== 'CLIENT_USER') {
    delete payload.clientId
  }
  try {
    await teamStore.inviteUser(payload, selectedAgencyId.value ?? undefined)
    showSnackbar('User invited successfully')
    closeInviteDialog()
  } catch { /* error shown via store */ }
}

async function fetchClients() {
  if (isOwnerAdmin.value && !selectedAgencyId.value) {
    clients.value = []
    return
  }

  clientsLoading.value = true
  try {
    const { data } = await api.get('/clients', {
      params: selectedAgencyId.value ? { agencyId: selectedAgencyId.value } : undefined
    })
    clients.value = ((data || []) as Array<{ id: string; name: string }>).map((c) => ({ id: c.id, name: c.name }))
  } finally {
    clientsLoading.value = false
  }
}

async function fetchAgencyOptions() {
  if (!isOwnerAdmin.value) return
  agenciesLoading.value = true
  try {
    const { data } = await api.get('/admin/agencies')
    agencyOptions.value = ((data || []) as Array<{ id: string; name: string }>).map((a) => ({ id: a.id, name: a.name }))
  } finally {
    agenciesLoading.value = false
  }
}

async function fetchUsersForContext() {
  if (isOwnerAdmin.value && !selectedAgencyId.value) {
    teamStore.users = []
    return
  }
  await teamStore.fetchUsers(selectedAgencyId.value ?? undefined)
}

async function onAgencyChange() {
  await Promise.all([fetchUsersForContext(), fetchClients()])
}

/* ── Permissions ── */
const showPermDialog = ref(false)
const permDialogUserId = ref('')
const permDialogUserName = ref('')

function openPermissions(user: TeamUser) {
  permDialogUserId.value = user.id
  permDialogUserName.value = user.displayName
  showPermDialog.value = true
}

/* ── Edit ── */
const showEditDialog = ref(false)
const editFormValid = ref(false)
const editUserId = ref('')
const editForm = ref({ displayName: '', role: '', status: '' })

function openEditDialog(user: TeamUser) {
  editUserId.value = user.id
  editForm.value = {
    displayName: user.displayName,
    role: user.role,
    status: user.status
  }
  showEditDialog.value = true
}

async function handleUpdate() {
  try {
    await teamStore.updateUser(editUserId.value, editForm.value, selectedAgencyId.value ?? undefined)
    showSnackbar('User updated')
    showEditDialog.value = false
  } catch { /* error shown via store */ }
}

/* ── Disable / Enable ── */
async function handleDisable(user: TeamUser) {
  try {
    await teamStore.disableUser(user.id, selectedAgencyId.value ?? undefined)
    showSnackbar(`${user.displayName} disabled`)
  } catch { /* */ }
}

async function handleEnable(user: TeamUser) {
  try {
    await teamStore.enableUser(user.id, selectedAgencyId.value ?? undefined)
    showSnackbar(`${user.displayName} enabled`)
  } catch { /* */ }
}

/* ── Helpers ── */
function roleColor(role: string) {
  switch (role) {
    case 'AGENCY_ADMIN': return 'primary'
    case 'AGENCY_USER': return 'info'
    case 'CLIENT_USER': return 'warning'
    default: return 'grey'
  }
}

function formatDate(iso: string) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString()
}

const snackbar = ref(false)
const snackbarText = ref('')
function showSnackbar(msg: string) {
  snackbarText.value = msg
  snackbar.value = true
}

/* ── Validation Rules ── */
const rules = {
  required: (v: string) => !!v || 'Required',
  email: (v: string) => /.+@.+\..+/.test(v) || 'Invalid email',
  minLength: (n: number) => (v: string) => (v && v.length >= n) || `Min ${n} characters`
}

onMounted(async () => {
  await fetchAgencyOptions()
  if (isOwnerAdmin.value) {
    return
  }
  await Promise.all([fetchUsersForContext(), fetchClients()])
})
</script>
