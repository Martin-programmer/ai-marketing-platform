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
          <v-list-item prepend-icon="mdi-brain" title="AI Audit" to="/owner/ai-audit" />
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
          <v-list-item prepend-icon="mdi-clipboard-text" title="Questionnaire" to="/portal/questionnaire">
            <template #append>
              <v-chip
                size="x-small"
                :color="portalQuestionnaireCompleted ? 'success' : 'orange-darken-2'"
                variant="flat"
              >
                <v-icon size="14">{{ portalQuestionnaireCompleted ? 'mdi-check' : 'mdi-alert-circle' }}</v-icon>
              </v-chip>
            </template>
          </v-list-item>
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
      <v-container v-if="showMetaBanner" fluid class="pb-0">
        <v-alert type="warning" variant="tonal" prominent>
          <div class="d-flex align-center flex-wrap ga-3">
            <div>
              <div class="font-weight-bold">Meta connections need attention</div>
              <div class="text-body-2">{{ metaBannerText }}</div>
            </div>
            <v-spacer />
            <v-btn
              color="warning"
              variant="outlined"
              :to="{ path: '/meta', query: { clientId: metaWarnings[0]?.clientId } }"
            >
              Open Meta
            </v-btn>
          </div>
        </v-alert>
      </v-container>
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
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import api from '@/api/client'
import portalApi from '@/api/portal'
import { useAuthStore } from '@/stores/auth'
import { useDashboardStore } from '@/stores/dashboard'
import { useRoute, useRouter } from 'vue-router'

const authStore = useAuthStore()
const dashStore = useDashboardStore()
const router = useRouter()
const route = useRoute()

const isAgencyAdmin = computed(() =>
  ['AGENCY_ADMIN', 'OWNER_ADMIN'].includes(authStore.userRole)
)
const isOwnerAdmin = computed(() => authStore.userRole === 'OWNER_ADMIN')
const isClientUser = computed(() => authStore.userRole === 'CLIENT_USER')
const portalQuestionnaireCompleted = ref(false)
const metaWarnings = ref<Array<{ clientId: string; clientName: string; status: string; daysUntilExpiry: number | null; tokenRefreshFailed: boolean }>>([])

const showMetaBanner = computed(() => !isOwnerAdmin.value && !isClientUser.value && metaWarnings.value.length > 0)
const metaBannerText = computed(() => {
  if (metaWarnings.value.length === 0) return ''
  if (metaWarnings.value.length === 1) {
    const warning = metaWarnings.value[0]
    if (!warning) return ''
    if (warning.status === 'TOKEN_EXPIRED') {
      return `${warning.clientName} needs a Meta reconnect now.`
    }
    if (warning.tokenRefreshFailed) {
      return `${warning.clientName} had an automatic token refresh failure.`
    }
    return `${warning.clientName} has a Meta token expiring in ${warning.daysUntilExpiry ?? 0} day(s).`
  }
  return `${metaWarnings.value.length} clients have Meta tokens that are expiring, expired, or failed to refresh.`
})

async function loadPortalQuestionnaireStatus() {
  if (!isClientUser.value) {
    portalQuestionnaireCompleted.value = false
    return
  }
  try {
    const { data } = await portalApi.getQuestionnaire()
    portalQuestionnaireCompleted.value = !!data?.questionnaireCompleted
  } catch {
    portalQuestionnaireCompleted.value = false
  }
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}

function handlePortalQuestionnaireUpdated() {
  loadPortalQuestionnaireStatus()
}

async function loadMetaWarnings() {
  if (!authStore.isAuthenticated || isOwnerAdmin.value || isClientUser.value) {
    metaWarnings.value = []
    return
  }
  try {
    const { data: clients } = await api.get('/clients')
    const warningResults = await Promise.all((clients || []).map(async (client: any) => {
      try {
        const { data: connection } = await api.get(`/clients/${client.id}/meta/connection`)
        if (!connection) return null
        const expiringSoon = connection.status === 'CONNECTED' && connection.daysUntilExpiry != null && connection.daysUntilExpiry <= 7
        const needsAttention = connection.status === 'TOKEN_EXPIRED' || connection.tokenRefreshFailed || expiringSoon
        if (!needsAttention) return null
        return {
          clientId: client.id,
          clientName: client.name,
          status: connection.status,
          daysUntilExpiry: connection.daysUntilExpiry,
          tokenRefreshFailed: !!connection.tokenRefreshFailed,
        }
      } catch {
        return null
      }
    }))
    metaWarnings.value = warningResults.filter((warning): warning is { clientId: string; clientName: string; status: string; daysUntilExpiry: number | null; tokenRefreshFailed: boolean } => !!warning)
  } catch {
    metaWarnings.value = []
  }
}

watch(isClientUser, () => {
  loadPortalQuestionnaireStatus()
}, { immediate: true })

watch(() => route.fullPath, () => {
  void loadMetaWarnings()
}, { immediate: true })

onMounted(() => {
  window.addEventListener('portal-questionnaire-updated', handlePortalQuestionnaireUpdated)
  void loadMetaWarnings()
})

onUnmounted(() => {
  window.removeEventListener('portal-questionnaire-updated', handlePortalQuestionnaireUpdated)
})
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
