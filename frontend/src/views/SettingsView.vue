<template>
  <div>
    <h1 class="text-h4 mb-6">Settings</h1>

    <!-- Agency-level 2FA toggle (AGENCY_ADMIN only) -->
    <v-card v-if="isAgencyAdmin" class="mb-6" max-width="720">
      <v-card-title class="d-flex align-center">
        <v-icon start>mdi-shield-lock</v-icon>
        Security
      </v-card-title>
      <v-card-text>
        <v-switch
          v-model="agencyTwoFactor"
          color="primary"
          :loading="agencyLoading"
          :disabled="agencyLoading"
          @update:model-value="toggleAgencyTwoFactor"
          hide-details
        >
          <template #label>
            <div>
              <div class="font-weight-medium">Require two-factor authentication for all users</div>
              <div class="text-body-2 text-medium-emphasis">
                When enabled, all users in your agency will be required to verify their identity via email code on each login.
              </div>
            </div>
          </template>
        </v-switch>
      </v-card-text>
    </v-card>

    <!-- Personal 2FA toggle (all users) -->
    <v-card max-width="720">
      <v-card-title class="d-flex align-center">
        <v-icon start>mdi-account-lock</v-icon>
        Personal Security
      </v-card-title>
      <v-card-text>
        <v-switch
          v-model="personalTwoFactor"
          color="primary"
          :loading="personalLoading"
          :disabled="personalLoading || agencyTwoFactor"
          @update:model-value="togglePersonalTwoFactor"
          hide-details
        >
          <template #label>
            <div>
              <div class="font-weight-medium">Enable two-factor authentication for my account</div>
              <div class="text-body-2 text-medium-emphasis">
                {{ agencyTwoFactor
                  ? 'Two-factor authentication is enforced by your agency administrator.'
                  : 'Adds an extra layer of security by requiring a verification code sent to your email on each login.'
                }}
              </div>
            </div>
          </template>
        </v-switch>
      </v-card-text>
    </v-card>

    <v-snackbar v-model="snackbar" :color="snackbarColor" timeout="3000" location="bottom end">
      {{ snackbarText }}
    </v-snackbar>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import api from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const isAgencyAdmin = computed(() =>
  ['AGENCY_ADMIN', 'OWNER_ADMIN'].includes(authStore.userRole)
)

const agencyTwoFactor = ref(false)
const personalTwoFactor = ref(false)
const agencyLoading = ref(false)
const personalLoading = ref(false)
const snackbar = ref(false)
const snackbarText = ref('')
const snackbarColor = ref<'success' | 'error'>('success')

function showSnackbar(text: string, color: 'success' | 'error' = 'success') {
  snackbarText.value = text
  snackbarColor.value = color
  snackbar.value = true
}

async function loadSettings() {
  try {
    if (isAgencyAdmin.value) {
      const { data } = await api.get('/agency/settings')
      agencyTwoFactor.value = data.twoFactorEnabled ?? false
    }

    const { data } = await api.get('/users/me/two-factor')
    personalTwoFactor.value = data.twoFactorEnabled ?? false
  } catch {
    showSnackbar('Failed to load settings', 'error')
  }
}

async function toggleAgencyTwoFactor(val: boolean | null) {
  agencyLoading.value = true
  try {
    await api.patch('/agency/settings', { twoFactorEnabled: !!val })
    agencyTwoFactor.value = !!val
    showSnackbar(val ? 'Two-factor authentication enabled for agency' : 'Two-factor authentication disabled for agency')
  } catch {
    agencyTwoFactor.value = !val
    showSnackbar('Failed to update agency setting', 'error')
  } finally {
    agencyLoading.value = false
  }
}

async function togglePersonalTwoFactor(val: boolean | null) {
  personalLoading.value = true
  try {
    await api.patch('/users/me/two-factor', { twoFactorEnabled: !!val })
    personalTwoFactor.value = !!val
    showSnackbar(val ? 'Two-factor authentication enabled' : 'Two-factor authentication disabled')
  } catch {
    personalTwoFactor.value = !val
    showSnackbar('Failed to update setting', 'error')
  } finally {
    personalLoading.value = false
  }
}

onMounted(() => {
  loadSettings()
})
</script>
