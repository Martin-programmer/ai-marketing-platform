<template>
  <!-- If not authenticated, show public view + footer -->
  <template v-if="!authStore.isAuthenticated">
    <router-view />
    <div class="global-legal-footer">
      <router-link to="/privacy" class="legal-link">Privacy Policy</router-link>
      <span class="mx-2">•</span>
      <router-link to="/terms" class="legal-link">Terms of Service</router-link>
    </div>
  </template>

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
        <!-- Owner Admin navigation -->
        <template v-if="isOwnerAdmin">
          <v-list-item prepend-icon="mdi-view-dashboard" title="Dashboard" to="/owner" />
          <v-list-item prepend-icon="mdi-domain" title="Agencies" to="/owner/agencies" />
          <v-divider class="my-2" />
          <v-list-item prepend-icon="mdi-account-multiple" title="Team" to="/team" />
          <v-list-item prepend-icon="mdi-history" title="Audit Log" to="/audit" />
          <v-list-item prepend-icon="mdi-shield-crown" title="Admin" to="/admin" />
        </template>

        <!-- Agency navigation -->
        <template v-else-if="!isClientUser">
          <v-list-item prepend-icon="mdi-view-dashboard" title="Dashboard" to="/" />
          <v-list-item prepend-icon="mdi-account-group" title="Clients" to="/clients" />
          <v-list-item prepend-icon="mdi-image-multiple" title="Creatives" to="/creatives" />
          <v-list-item prepend-icon="mdi-bullhorn" title="Campaigns" to="/campaigns" />
          <v-list-item prepend-icon="mdi-lightbulb-on" title="Suggestions" to="/suggestions">
            <template #append>
              <v-badge
                v-if="dashStore.highAnomalyCount > 0"
                :content="dashStore.highAnomalyCount"
                color="error"
                inline
              />
            </template>
          </v-list-item>
          <v-list-item prepend-icon="mdi-file-chart" title="Reports" to="/reports" />
          <v-list-item prepend-icon="mdi-facebook" title="Meta" to="/meta" />
          <v-list-item prepend-icon="mdi-history" title="Audit Log" to="/audit" />

          <v-divider v-if="isAgencyAdmin" class="my-2" />
          <v-list-item v-if="isAgencyAdmin" prepend-icon="mdi-account-multiple" title="Team" to="/team" />
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
      <v-app-bar-title>
        {{ isOwnerAdmin ? 'Platform Admin' : isClientUser ? 'Client Portal' : 'AI Marketing Platform' }}
      </v-app-bar-title>
    </v-app-bar>

    <v-main>
      <v-container fluid>
        <router-view />
      </v-container>
    </v-main>

    <v-footer app height="40" class="justify-center text-caption text-medium-emphasis">
      <router-link to="/privacy" class="legal-link">Privacy Policy</router-link>
      <span class="mx-2">•</span>
      <router-link to="/terms" class="legal-link">Terms of Service</router-link>
    </v-footer>
  </v-app>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useDashboardStore } from '@/stores/dashboard'
import { useRouter } from 'vue-router'

const authStore = useAuthStore()
const dashStore = useDashboardStore()
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

<style scoped>
.global-legal-footer {
  text-align: center;
  margin: 12px 0 20px;
  color: rgba(0, 0, 0, 0.55);
  font-size: 12px;
}

.legal-link {
  color: rgba(0, 0, 0, 0.62);
  text-decoration: none;
}

.legal-link:hover {
  text-decoration: underline;
}
</style>
