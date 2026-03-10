<template>
  <div>
    <div class="d-flex align-center mb-4">
      <h1>Admin Panel</h1>
      <v-spacer />
      <v-btn color="primary" prepend-icon="mdi-plus" @click="showCreate = true">
        Create Agency
      </v-btn>
    </div>

    <v-progress-linear v-if="adminStore.loading" indeterminate color="primary" class="mb-4" />
    <v-alert v-if="adminStore.error" type="error" class="mb-4" closable @click:close="adminStore.error = null">
      {{ adminStore.error }}
    </v-alert>

    <v-card>
      <v-data-table
        :items="adminStore.agencies"
        :headers="headers"
        :loading="adminStore.loading"
        hover
      >
        <template #item.status="{ item }">
          <v-chip
            :color="item.status === 'ACTIVE' ? 'success' : item.status === 'SUSPENDED' ? 'error' : 'grey'"
            size="small"
          >
            {{ item.status }}
          </v-chip>
        </template>

        <template #item.planCode="{ item }">
          <v-chip size="small" variant="outlined">{{ item.planCode }}</v-chip>
        </template>

        <template #item.createdAt="{ item }">
          {{ new Date(item.createdAt).toLocaleDateString() }}
        </template>

        <template #item.actions="{ item }">
          <v-btn
            v-if="item.status === 'ACTIVE'"
            size="small"
            color="error"
            variant="text"
            @click="onSuspend(item.id)"
          >
            Suspend
          </v-btn>
          <v-btn
            v-if="item.status === 'SUSPENDED'"
            size="small"
            color="success"
            variant="text"
            @click="onReactivate(item.id)"
          >
            Reactivate
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <!-- Create Agency Dialog -->
    <v-dialog v-model="showCreate" max-width="600" persistent>
      <v-card title="Create Agency">
        <v-card-text>
          <v-text-field
            v-model="form.name"
            label="Agency Name"
            :rules="[v => !!v || 'Required']"
            class="mb-2"
          />
          <v-select
            v-model="form.planCode"
            :items="['FREE', 'PRO', 'ENTERPRISE']"
            label="Plan"
            class="mb-2"
          />
          <v-divider class="my-3" />
          <div class="text-subtitle-2 mb-2">Initial Admin User</div>
          <v-text-field
            v-model="form.adminEmail"
            label="Admin Email"
            type="email"
            :rules="[v => !!v || 'Required']"
            class="mb-2"
          />
          <v-text-field
            v-model="form.adminDisplayName"
            label="Admin Display Name"
            class="mb-2"
          />
          <v-alert type="info" variant="tonal" density="compact" class="mt-2">
            An invitation email will be sent to the admin. They will set their own password.
          </v-alert>
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreate = false">Cancel</v-btn>
          <v-btn
            color="primary"
            :loading="adminStore.loading"
            :disabled="!form.name || !form.adminEmail"
            @click="onCreate"
          >
            Create
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <!-- Success snackbar -->
    <v-snackbar v-model="snackbar" color="success" timeout="3000">
      {{ snackbarText }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useAdminStore } from '@/stores/admin'

const adminStore = useAdminStore()

const showCreate = ref(false)
const snackbar = ref(false)
const snackbarText = ref('')

const form = reactive({
  name: '',
  planCode: 'FREE',
  adminEmail: '',
  adminDisplayName: 'Agency Admin',
})

const headers = [
  { title: 'Name', key: 'name' },
  { title: 'Plan', key: 'planCode' },
  { title: 'Status', key: 'status' },
  { title: 'Created', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false },
]

function resetForm() {
  form.name = ''
  form.planCode = 'FREE'
  form.adminEmail = ''
  form.adminDisplayName = 'Agency Admin'
}

async function onCreate() {
  try {
    await adminStore.createAgency({ ...form })
    showCreate.value = false
    resetForm()
    snackbarText.value = 'Agency created successfully'
    snackbar.value = true
  } catch {
    // error is shown via adminStore.error
  }
}

async function onSuspend(agencyId: string) {
  await adminStore.suspendAgency(agencyId)
  snackbarText.value = 'Agency suspended'
  snackbar.value = true
}

async function onReactivate(agencyId: string) {
  await adminStore.reactivateAgency(agencyId)
  snackbarText.value = 'Agency reactivated'
  snackbar.value = true
}

onMounted(() => {
  adminStore.fetchAgencies()
})
</script>
