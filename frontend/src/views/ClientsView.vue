<template>
  <div>
    <div class="d-flex justify-space-between align-center mb-4">
      <h1>Clients</h1>
      <v-btn color="primary" @click="showCreate = true">
        <v-icon start>mdi-plus</v-icon>
        New Client
      </v-btn>
    </div>

    <v-alert v-if="store.error" type="error" class="mb-4">{{ store.error }}</v-alert>

    <v-card>
      <v-data-table
        :headers="headers"
        :items="store.clients"
        :loading="store.loading"
        item-value="id"
        hover
      >
        <template #item.status="{ item }">
          <v-chip :color="item.status === 'ACTIVE' ? 'success' : 'warning'" size="small">
            {{ item.status }}
          </v-chip>
        </template>
        <template #item.createdAt="{ item }">
          {{ new Date(item.createdAt).toLocaleDateString() }}
        </template>
        <template #item.actions="{ item }">
          <v-btn size="small" variant="text" :to="`/clients/${item.id}`">
            <v-icon>mdi-eye</v-icon>
          </v-btn>
          <v-btn v-if="item.status === 'ACTIVE'" size="small" variant="text" color="warning" @click="store.pauseClient(item.id)">
            <v-icon>mdi-pause</v-icon>
          </v-btn>
          <v-btn v-else size="small" variant="text" color="success" @click="store.activateClient(item.id)">
            <v-icon>mdi-play</v-icon>
          </v-btn>
        </template>
      </v-data-table>
    </v-card>

    <!-- Create Dialog -->
    <v-dialog v-model="showCreate" max-width="500">
      <v-card title="New Client">
        <v-card-text>
          <v-text-field v-model="form.name" label="Name" required />
          <v-text-field v-model="form.industry" label="Industry" />
          <v-text-field v-model="form.timezone" label="Timezone" placeholder="Europe/Sofia" />
          <v-text-field v-model="form.currency" label="Currency" placeholder="BGN" />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="showCreate = false">Cancel</v-btn>
          <v-btn color="primary" @click="onCreate">Create</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useClientStore } from '@/stores/clients'

const store = useClientStore()
const showCreate = ref(false)
const form = ref({ name: '', industry: '', timezone: 'Europe/Sofia', currency: 'BGN' })

const headers = [
  { title: 'Name', key: 'name' },
  { title: 'Industry', key: 'industry' },
  { title: 'Status', key: 'status' },
  { title: 'Currency', key: 'currency' },
  { title: 'Created', key: 'createdAt' },
  { title: 'Actions', key: 'actions', sortable: false },
]

onMounted(() => store.fetchClients())

async function onCreate() {
  await store.createClient(form.value)
  showCreate.value = false
  form.value = { name: '', industry: '', timezone: 'Europe/Sofia', currency: 'BGN' }
}
</script>
