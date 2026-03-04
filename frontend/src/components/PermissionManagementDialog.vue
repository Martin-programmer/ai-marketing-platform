<template>
  <v-dialog :model-value="modelValue" max-width="780" persistent @update:model-value="$emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="d-flex align-center">
        <v-icon class="mr-2">mdi-shield-key</v-icon>
        Permissions for: {{ userName }}
      </v-card-title>

      <v-card-text>
        <v-progress-linear v-if="loading" indeterminate color="primary" class="mb-4" />

        <v-alert v-if="errorMsg" type="error" closable class="mb-4" @click:close="errorMsg = null">
          {{ errorMsg }}
        </v-alert>

        <v-alert v-if="!loading && clients.length === 0" type="info" variant="tonal" class="mb-4">
          No clients found in this agency.
        </v-alert>

        <!-- Per-client permission rows -->
        <v-table v-if="clients.length > 0" density="comfortable">
          <thead>
            <tr>
              <th style="width: 200px">Client</th>
              <th>Access Level</th>
              <th style="width: 60px"></th>
            </tr>
          </thead>
          <tbody>
            <template v-for="client in clients" :key="client.id">
              <tr>
                <td class="font-weight-medium">{{ client.name }}</td>
                <td>
                  <v-btn-toggle
                    :model-value="currentPreset(client.id)"
                    @update:model-value="(v: string) => applyPreset(client.id, v)"
                    density="compact"
                    divided
                    variant="outlined"
                    color="primary"
                    mandatory
                  >
                    <v-btn value="NONE" size="small">None</v-btn>
                    <v-btn value="READ_ONLY" size="small">Read Only</v-btn>
                    <v-btn value="EDITOR" size="small">Editor</v-btn>
                    <v-btn value="FULL" size="small">Full</v-btn>
                  </v-btn-toggle>
                  <v-chip
                    v-if="currentPreset(client.id) === 'CUSTOM'"
                    size="small"
                    color="purple"
                    variant="tonal"
                    class="ml-2"
                  >
                    Custom
                  </v-chip>
                </td>
                <td>
                  <v-btn
                    icon
                    size="small"
                    variant="text"
                    @click="toggleExpand(client.id)"
                  >
                    <v-icon>{{ expanded[client.id] ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
                  </v-btn>
                </td>
              </tr>

              <!-- Expanded individual permissions -->
              <tr v-if="expanded[client.id]">
                <td colspan="3" class="pa-4 bg-grey-lighten-5">
                  <v-row dense>
                    <v-col v-for="perm in allPermissions" :key="perm" cols="12" sm="6" md="4">
                      <v-checkbox
                        :model-value="hasPermission(client.id, perm)"
                        @update:model-value="togglePermission(client.id, perm, $event as boolean)"
                        :label="permissionLabel(perm)"
                        density="compact"
                        hide-details
                        color="primary"
                      />
                    </v-col>
                  </v-row>
                </td>
              </tr>
            </template>
          </tbody>
        </v-table>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn text @click="close">Cancel</v-btn>
        <v-btn color="primary" :loading="saving" @click="saveAll">
          Save
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import api from '@/api/client'
import { permissionsApi } from '@/api/permissions'

const props = defineProps<{
  modelValue: boolean
  userId: string
  userName: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

/* ── All 13 permissions in display order ── */
const allPermissions = [
  'CLIENT_VIEW', 'CLIENT_EDIT',
  'CAMPAIGNS_VIEW', 'CAMPAIGNS_EDIT', 'CAMPAIGNS_PUBLISH',
  'CREATIVES_VIEW', 'CREATIVES_EDIT',
  'REPORTS_VIEW', 'REPORTS_EDIT', 'REPORTS_SEND',
  'META_MANAGE',
  'AI_VIEW', 'AI_APPROVE',
]

const readOnlyPerms = new Set([
  'CLIENT_VIEW', 'CAMPAIGNS_VIEW', 'CREATIVES_VIEW', 'REPORTS_VIEW', 'AI_VIEW',
])

const editorPerms = new Set([
  'CLIENT_VIEW', 'CLIENT_EDIT',
  'CAMPAIGNS_VIEW', 'CAMPAIGNS_EDIT',
  'CREATIVES_VIEW', 'CREATIVES_EDIT',
  'REPORTS_VIEW', 'REPORTS_EDIT',
  'AI_VIEW',
])

const fullPerms = new Set(allPermissions)

interface ClientInfo {
  id: string
  name: string
}

const clients = ref<ClientInfo[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMsg = ref<string | null>(null)

/** clientId → Set<permission> */
const permMap = ref<Record<string, Set<string>>>({})

/** which client rows are expanded */
const expanded = ref<Record<string, boolean>>({})

/* ── Load data when dialog opens ── */
watch(() => props.modelValue, async (open) => {
  if (open && props.userId) {
    await loadData()
  }
})

async function loadData() {
  loading.value = true
  errorMsg.value = null
  try {
    const [clientsRes, permsRes] = await Promise.all([
      api.get('/clients'),
      permissionsApi.getUserPermissions(props.userId),
    ])
    clients.value = (clientsRes.data as ClientInfo[]).map(c => ({ id: c.id, name: (c as any).name }))

    // Build permission map from response: array of { clientId, permission }
    const map: Record<string, Set<string>> = {}
    for (const c of clients.value) {
      map[c.id] = new Set()
    }
    for (const entry of permsRes.data as Array<{ clientId: string; permission: string }>) {
      if (!map[entry.clientId]) map[entry.clientId] = new Set()
      map[entry.clientId]!.add(entry.permission)
    }
    permMap.value = map
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || 'Failed to load permissions'
  } finally {
    loading.value = false
  }
}

/* ── Preset detection ── */
function currentPreset(clientId: string): string {
  const perms = permMap.value[clientId] || new Set()
  if (perms.size === 0) return 'NONE'
  if (setsEqual(perms, readOnlyPerms)) return 'READ_ONLY'
  if (setsEqual(perms, editorPerms)) return 'EDITOR'
  if (setsEqual(perms, fullPerms)) return 'FULL'
  return 'CUSTOM'
}

function setsEqual(a: Set<string>, b: Set<string>): boolean {
  if (a.size !== b.size) return false
  for (const v of a) if (!b.has(v)) return false
  return true
}

/* ── Preset application (local only) ── */
function applyPreset(clientId: string, preset: string) {
  switch (preset) {
    case 'NONE':
      permMap.value[clientId] = new Set()
      break
    case 'READ_ONLY':
      permMap.value[clientId] = new Set(readOnlyPerms)
      break
    case 'EDITOR':
      permMap.value[clientId] = new Set(editorPerms)
      break
    case 'FULL':
      permMap.value[clientId] = new Set(fullPerms)
      break
  }
}

/* ── Individual toggles ── */
function hasPermission(clientId: string, perm: string): boolean {
  return permMap.value[clientId]?.has(perm) ?? false
}

function togglePermission(clientId: string, perm: string, on: boolean) {
  if (!permMap.value[clientId]) permMap.value[clientId] = new Set()
  if (on) {
    permMap.value[clientId].add(perm)
  } else {
    permMap.value[clientId].delete(perm)
  }
  // Force reactivity
  permMap.value = { ...permMap.value }
}

function toggleExpand(clientId: string) {
  expanded.value[clientId] = !expanded.value[clientId]
}

/* ── Labels ── */
function permissionLabel(perm: string): string {
  const labels: Record<string, string> = {
    CLIENT_VIEW: 'View client data',
    CLIENT_EDIT: 'Edit client profile',
    CAMPAIGNS_VIEW: 'View campaigns',
    CAMPAIGNS_EDIT: 'Edit campaigns',
    CAMPAIGNS_PUBLISH: 'Publish campaigns',
    CREATIVES_VIEW: 'View creatives',
    CREATIVES_EDIT: 'Edit creatives',
    REPORTS_VIEW: 'View reports',
    REPORTS_EDIT: 'Edit reports',
    REPORTS_SEND: 'Send reports',
    META_MANAGE: 'Manage Meta integration',
    AI_VIEW: 'View AI suggestions',
    AI_APPROVE: 'Approve AI suggestions',
  }
  return labels[perm] || perm
}

/* ── Save ── */
async function saveAll() {
  saving.value = true
  errorMsg.value = null
  try {
    const promises = clients.value.map(client => {
      const perms = Array.from(permMap.value[client.id] || [])
      if (perms.length === 0) {
        return permissionsApi.removePermissions(props.userId, client.id).catch(() => {})
      }
      return permissionsApi.setPermissions(props.userId, client.id, perms)
    })
    await Promise.all(promises)
    emit('saved')
    close()
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || 'Failed to save permissions'
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>
