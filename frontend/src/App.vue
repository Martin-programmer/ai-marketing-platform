<template>
  <!-- If not authenticated, show only router-view (login screen) -->
  <router-view v-if="!authStore.isAuthenticated" />

  <!-- If authenticated, show navigation layout -->
  <v-app v-else>
    <v-navigation-drawer permanent>
      <v-list-item
        :title="authStore.displayName"
        :subtitle="authStore.userRole"
        class="pa-4"
      >
        <template v-slot:prepend>
          <v-avatar color="primary" size="40">
            <span class="text-h6 text-white">{{ authStore.displayName?.charAt(0)?.toUpperCase() }}</span>
          </v-avatar>
        </template>
      </v-list-item>

      <v-divider />

      <v-list nav density="compact">
        <!-- Agency navigation -->
        <template v-if="!isClientUser">
          <v-list-item prepend-icon="mdi-view-dashboard" title="Dashboard" to="/" />
          <v-list-item prepend-icon="mdi-account-group" title="Clients" to="/clients" />
          <v-list-item prepend-icon="mdi-image-multiple" title="Creatives" to="/creatives" />
          <v-list-item prepend-icon="mdi-bullhorn" title="Campaigns" to="/campaigns" />
          <v-list-item prepend-icon="mdi-lightbulb-on" title="Suggestions" to="/suggestions" />
          <v-list-item prepend-icon="mdi-file-chart" title="Reports" to="/reports" />
          <v-list-item prepend-icon="mdi-facebook" title="Meta" to="/meta" />
          <v-list-item prepend-icon="mdi-history" title="Audit Log" to="/audit" />

          <v-divider v-if="isAgencyAdmin" class="my-2" />
          <v-list-item v-if="isAgencyAdmin" prepend-icon="mdi-account-multiple" title="Team" to="/team" />
          <v-list-item v-if="isOwnerAdmin" prepend-icon="mdi-shield-crown" title="Admin" to="/admin" />
        </template>

        <!-- Client Portal navigation -->
        <template v-else>
          <v-list-item prepend-icon="mdi-view-dashboard" title="Dashboard" to="/portal" />
          <v-list-item prepend-icon="mdi-file-chart" title="Reports" to="/portal/reports" />
          <v-list-item prepend-icon="mdi-bullhorn" title="Campaigns" to="/portal/campaigns" />
          <v-list-item prepend-icon="mdi-lightbulb-on" title="Activity" to="/portal/suggestions" />
          <v-list-item prepend-icon="mdi-information" title="Profile" to="/portal/profile" />
        </template>
      </v-list>

      <template v-slot:append>
        <div class="pa-2">
          <v-btn block variant="tonal" color="error" prepend-icon="mdi-logout" @click="handleLogout">
            Logout
          </v-btn>
        </div>
      </template>
    </v-navigation-drawer>

    <v-app-bar flat border>
      <v-app-bar-title>{{ isClientUser ? 'Client Portal' : 'AI Marketing Platform' }}</v-app-bar-title>
    </v-app-bar>

    <v-main>
      <v-container fluid>
        <router-view />
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const router = useRouter()

const isAgencyAdmin = computed(() =>
  ['AGENCY_ADMIN', 'OWNER_ADMIN'].includes(authStore.userRole)
)
const isOwnerAdmin = computed(() => authStore.userRole === 'OWNER_ADMIN')
const isClientUser = computed(() => authStore.userRole === 'CLIENT_USER')

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>
