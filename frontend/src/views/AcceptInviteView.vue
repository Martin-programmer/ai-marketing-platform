<template>
  <v-app>
    <v-main class="d-flex align-center justify-center" style="min-height: 100vh; background: linear-gradient(135deg, #1565C0 0%, #0D47A1 100%);">
      <v-card width="480" class="pa-8" elevation="12" rounded="lg">
        <div class="text-center mb-6">
          <v-icon size="48" color="primary" class="mb-2">mdi-account-check</v-icon>
          <h1 class="text-h5 font-weight-bold">Accept Invitation</h1>
        </div>

        <!-- Loading state -->
        <div v-if="loading" class="text-center py-8">
          <v-progress-circular indeterminate color="primary" size="48" />
          <p class="text-body-2 text-grey mt-4">Loading invitation details...</p>
        </div>

        <!-- Error state -->
        <v-alert v-else-if="errorMessage" type="error" variant="tonal" class="mb-4">
          {{ errorMessage }}
          <template v-slot:append>
            <v-btn variant="text" size="small" to="/login">Go to Login</v-btn>
          </template>
        </v-alert>

        <!-- Expired state -->
        <div v-else-if="inviteInfo?.expired" class="text-center py-4">
          <v-icon size="64" color="warning" class="mb-4">mdi-clock-alert</v-icon>
          <h2 class="text-h6 mb-2">Invitation Expired</h2>
          <p class="text-body-2 text-grey mb-4">
            This invitation has expired. Please ask your administrator to send a new one.
          </p>
          <v-btn color="primary" to="/login">Go to Login</v-btn>
        </div>

        <!-- Invitation form -->
        <div v-else-if="inviteInfo">
          <v-alert type="info" variant="tonal" class="mb-4">
            <strong>Welcome!</strong> You've been invited as
            <v-chip size="small" color="primary" label class="mx-1">{{ inviteInfo.role }}</v-chip>
            by <strong>{{ inviteInfo.agencyName }}</strong>
          </v-alert>

          <p class="text-body-2 text-grey mb-4">
            Email: <strong>{{ inviteInfo.email }}</strong>
          </p>

          <v-form ref="formRef" v-model="formValid" @submit.prevent="handleAccept">
            <v-text-field
              v-model="form.displayName"
              label="Display Name"
              prepend-inner-icon="mdi-account-outline"
              variant="outlined"
              density="comfortable"
              class="mb-2"
              :rules="[rules.required]"
              :disabled="submitting"
            />

            <v-text-field
              v-model="form.password"
              :type="showPassword ? 'text' : 'password'"
              label="Password"
              prepend-inner-icon="mdi-lock-outline"
              :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
              @click:append-inner="showPassword = !showPassword"
              variant="outlined"
              density="comfortable"
              class="mb-2"
              :rules="[rules.required, rules.minLength(6)]"
              :disabled="submitting"
            />

            <v-text-field
              v-model="form.confirmPassword"
              :type="showPassword ? 'text' : 'password'"
              label="Confirm Password"
              prepend-inner-icon="mdi-lock-check-outline"
              variant="outlined"
              density="comfortable"
              class="mb-4"
              :rules="[rules.required, rules.passwordMatch]"
              :disabled="submitting"
            />

            <v-alert v-if="submitError" type="error" variant="tonal" class="mb-4" closable @click:close="submitError = null">
              {{ submitError }}
            </v-alert>

            <v-btn
              block
              size="large"
              color="primary"
              type="submit"
              :loading="submitting"
              :disabled="!formValid"
            >
              Activate Account
            </v-btn>
          </v-form>
        </div>
      </v-card>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '@/api/client'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

interface InviteInfo {
  email: string
  role: string
  agencyName: string
  expired: boolean
}

const loading = ref(true)
const errorMessage = ref<string | null>(null)
const inviteInfo = ref<InviteInfo | null>(null)
const formValid = ref(false)
const submitting = ref(false)
const submitError = ref<string | null>(null)
const showPassword = ref(false)

const form = ref({
  displayName: '',
  password: '',
  confirmPassword: ''
})

const rules = {
  required: (v: string) => !!v || 'Required',
  minLength: (n: number) => (v: string) => (v && v.length >= n) || `Min ${n} characters`,
  passwordMatch: (v: string) => v === form.value.password || 'Passwords do not match'
}

const formRef = ref()

onMounted(async () => {
  const token = route.query.token as string
  if (!token) {
    errorMessage.value = 'No invitation token provided.'
    loading.value = false
    return
  }

  try {
    const { data } = await api.get('/auth/invite-info', { params: { token } })
    inviteInfo.value = data
  } catch (e: any) {
    errorMessage.value = e.response?.data?.message || 'Invalid or expired invitation link.'
  } finally {
    loading.value = false
  }
})

async function handleAccept() {
  if (!formValid.value) return
  submitting.value = true
  submitError.value = null

  const token = route.query.token as string

  try {
    const { data } = await api.post('/auth/accept-invite', {
      token,
      displayName: form.value.displayName,
      password: form.value.password
    })

    // Auto-login: store tokens
    localStorage.setItem('accessToken', data.accessToken)
    localStorage.setItem('refreshToken', data.refreshToken)
    localStorage.setItem('user', JSON.stringify(data.user))

    authStore.accessToken = data.accessToken
    authStore.refreshToken = data.refreshToken
    authStore.user = data.user

    // Redirect based on role
    if (data.user.role === 'CLIENT_USER') {
      router.push('/portal')
    } else if (data.user.role === 'OWNER_ADMIN') {
      router.push('/owner')
    } else {
      router.push('/')
    }
  } catch (e: any) {
    submitError.value = e.response?.data?.message || 'Failed to activate account. Please try again.'
  } finally {
    submitting.value = false
  }
}
</script>
