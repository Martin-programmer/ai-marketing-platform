<template>
  <div>
    <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />
    <v-alert v-if="error" type="error" class="mb-4">{{ error }}</v-alert>

    <template v-if="client">
      <div class="d-flex align-center mb-4">
        <v-btn icon variant="text" :to="'/clients'" class="mr-2">
          <v-icon>mdi-arrow-left</v-icon>
        </v-btn>
        <h1>{{ client.name }}</h1>
        <v-chip :color="client.status === 'ACTIVE' ? 'success' : 'warning'" size="small" class="ml-3">
          {{ client.status }}
        </v-chip>
      </div>

      <!-- Client Info Card -->
      <v-card class="mb-6">
        <v-card-title>Client Information</v-card-title>
        <v-card-text>
          <v-row>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Industry</div>
              <div>{{ client.industry || '—' }}</div>
            </v-col>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Timezone</div>
              <div>{{ client.timezone }}</div>
            </v-col>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Currency</div>
              <div>{{ client.currency }}</div>
            </v-col>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Status</div>
              <div>{{ client.status }}</div>
            </v-col>
            <v-col cols="12" sm="4">
              <div class="text-caption text-grey">Created</div>
              <div>{{ new Date(client.createdAt).toLocaleDateString() }}</div>
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <!-- Client Profile Section -->
      <v-card class="mb-6">
        <v-card-title class="d-flex align-center">
          Profile
          <v-spacer />
          <v-btn size="small" variant="outlined" @click="showEditProfile = true">
            <v-icon start>mdi-pencil</v-icon> Edit
          </v-btn>
        </v-card-title>
        <v-card-text v-if="profile">
          <v-row>
            <v-col cols="12" sm="6">
              <div class="text-caption text-grey">Website</div>
              <div>{{ profile.website || '—' }}</div>
            </v-col>
          </v-row>
          <div class="mt-3">
            <div class="text-caption text-grey">Profile Data</div>
            <pre class="mt-1 pa-2 bg-grey-lighten-5 rounded" style="white-space: pre-wrap; font-size: 0.85em">{{ formatJson(profile.profileJson) }}</pre>
          </div>
        </v-card-text>
        <v-card-text v-else>
          <p class="text-grey">No profile data available.</p>
        </v-card-text>
      </v-card>

      <!-- Quick Links -->
      <h2 class="mb-3">Quick Links</h2>
      <v-row class="mb-6">
        <v-col v-for="link in quickLinks" :key="link.to" cols="12" sm="6" md="3">
          <v-card :to="link.to" hover variant="outlined">
            <v-card-text class="text-center">
              <v-icon size="36" color="primary" class="mb-2">{{ link.icon }}</v-icon>
              <div class="text-subtitle-1">{{ link.label }}</div>
            </v-card-text>
          </v-card>
        </v-col>
      </v-row>

      <!-- Permissions Section (ADMIN only) -->
      <v-card v-if="isAdmin" class="mb-6">
        <v-card-title class="d-flex align-center">
          Permissions
          <v-spacer />
          <v-btn size="small" color="primary" prepend-icon="mdi-plus" @click="showAddPermission = true">
            Add Permission
          </v-btn>
        </v-card-title>
        <v-card-text>
          <v-data-table
            :headers="permHeaders"
            :items="permissions"
            :loading="permLoading"
            density="compact"
            item-value="userId"
          >
            <template #item.permission="{ item }">
              <v-chip size="small" :color="permColor(item.permission)">{{ item.permission }}</v-chip>
            </template>
            <template #item.actions="{ item }">
              <v-btn icon="mdi-delete" size="small" variant="text" color="error" @click="removePermission(item.userId)" />
            </template>
          </v-data-table>
        </v-card-text>
      </v-card>

      <!-- Recent Activity -->
      <v-card>
        <v-card-title>Recent Activity</v-card-title>
        <v-card-text>
          <p class="text-grey">Activity timeline coming soon...</p>
        </v-card-text>
      </v-card>
    </template>

    <!-- Add Permission Dialog -->
    <v-dialog v-model="showAddPermission" max-width="440" persistent>
      <v-card title="Add Permission">
        <v-card-text>
          <v-text-field v-model="addPermForm.userId" label="User ID" class="mb-2" />
          <v-select
            v-model="addPermForm.permission"
            label="Permission"
            :items="permLevels"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn text @click="showAddPermission = false">Cancel</v-btn>
          <v-btn color="primary" @click="handleAddPermission">Add</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Edit Profile Dialog -->
    <v-dialog v-model="showEditProfile" max-width="600">
      <v-card title="Edit Profile">
        <v-card-text>
          <v-text-field v-model="profileForm.website" label="Website" />
          <v-textarea
            v-model="profileForm.profileJson"
            label="Profile JSON"
            rows="8"
            auto-grow
            monospace
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showEditProfile = false">Cancel</v-btn>
          <v-btn color="primary" @click="onSaveProfile">Save</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import api from '@/api/client'

const route = useRoute()
const authStore = useAuthStore()
const clientId = computed(() => route.params.id as string)

const isAdmin = computed(() => ['AGENCY_ADMIN', 'OWNER_ADMIN'].includes(authStore.userRole))

const client = ref<any>(null)
const profile = ref<any>(null)
const loading = ref(false)
const error = ref<string | null>(null)
const showEditProfile = ref(false)
const profileForm = ref({ website: '', profileJson: '' })

/* ── Permissions ── */
const permissions = ref<any[]>([])
const permLoading = ref(false)
const showAddPermission = ref(false)
const addPermForm = ref({ userId: '', permission: 'READ' })
const permLevels = ['READ', 'WRITE', 'APPROVE', 'ADMIN']
const permHeaders = [
  { title: 'User', key: 'userDisplayName' },
  { title: 'Email', key: 'userEmail' },
  { title: 'Permission', key: 'permission' },
  { title: 'Actions', key: 'actions', sortable: false, align: 'end' as const }
]

function permColor(p: string) {
  switch (p) {
    case 'ADMIN': return 'error'
    case 'APPROVE': return 'warning'
    case 'WRITE': return 'primary'
    default: return 'info'
  }
}

async function fetchPermissions() {
  if (!isAdmin.value) return
  permLoading.value = true
  try {
    const { data } = await api.get(`/clients/${clientId.value}/permissions`)
    permissions.value = data
  } catch { /* ignore */ }
  finally { permLoading.value = false }
}

async function handleAddPermission() {
  try {
    await api.post(`/clients/${clientId.value}/permissions/add`, addPermForm.value)
    showAddPermission.value = false
    addPermForm.value = { userId: '', permission: 'READ' }
    await fetchPermissions()
  } catch (e: any) { error.value = e.response?.data?.message || 'Failed to add permission' }
}

async function removePermission(userId: string) {
  try {
    await api.delete(`/clients/${clientId.value}/permissions/${userId}`)
    await fetchPermissions()
  } catch (e: any) { error.value = e.response?.data?.message || 'Failed to remove permission' }
}

const quickLinks = computed(() => [
  { label: 'Campaigns', icon: 'mdi-bullhorn', to: '/campaigns' },
  { label: 'Creatives', icon: 'mdi-image-multiple', to: '/creatives' },
  { label: 'AI Suggestions', icon: 'mdi-lightbulb', to: '/suggestions' },
  { label: 'Reports', icon: 'mdi-file-chart', to: '/reports' },
])

function formatJson(val: any) {
  if (!val) return '—'
  try {
    return typeof val === 'string' ? JSON.stringify(JSON.parse(val), null, 2) : JSON.stringify(val, null, 2)
  } catch {
    return String(val)
  }
}

async function fetchClient() {
  loading.value = true
  error.value = null
  try {
    const { data } = await api.get(`/clients/${clientId.value}`)
    client.value = data
  } catch (e: any) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

async function fetchProfile() {
  try {
    const { data } = await api.get(`/clients/${clientId.value}/profile`)
    profile.value = data
    profileForm.value = {
      website: data.website || '',
      profileJson: data.profileJson ? (typeof data.profileJson === 'string' ? data.profileJson : JSON.stringify(data.profileJson, null, 2)) : '',
    }
  } catch {
    profile.value = null
  }
}

async function onSaveProfile() {
  try {
    let parsedJson = null
    if (profileForm.value.profileJson) {
      try { parsedJson = JSON.parse(profileForm.value.profileJson) } catch { parsedJson = profileForm.value.profileJson }
    }
    await api.put(`/clients/${clientId.value}/profile`, {
      website: profileForm.value.website,
      profileJson: parsedJson,
    })
    showEditProfile.value = false
    await fetchProfile()
  } catch (e: any) {
    error.value = e.message
  }
}

onMounted(() => {
  fetchClient()
  fetchProfile()
  fetchPermissions()
})
</script>
