<template>
  <v-app>
    <v-main class="d-flex align-center justify-center" style="min-height: 100vh; background: linear-gradient(135deg, #1565C0 0%, #0D47A1 100%);">
      <v-card width="420" class="pa-8" elevation="12" rounded="lg">
        <div class="text-center mb-6">
          <v-icon size="48" color="primary" class="mb-2">mdi-rocket-launch</v-icon>
          <h1 class="text-h5 font-weight-bold">AI Marketing Platform</h1>
          <p class="text-body-2 text-grey mt-1">Sign in to your account</p>
        </div>

        <v-alert v-if="authStore.error" type="error" variant="tonal" class="mb-4" closable @click:close="authStore.error = null">
          {{ authStore.error }}
        </v-alert>

        <v-text-field
          v-model="email"
          label="Email"
          type="email"
          prepend-inner-icon="mdi-email-outline"
          variant="outlined"
          density="comfortable"
          class="mb-2"
          @keyup.enter="handleLogin"
          :disabled="authStore.loading"
        />

        <v-text-field
          v-model="password"
          :type="showPassword ? 'text' : 'password'"
          label="Password"
          prepend-inner-icon="mdi-lock-outline"
          :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
          @click:append-inner="showPassword = !showPassword"
          variant="outlined"
          density="comfortable"
          class="mb-4"
          @keyup.enter="handleLogin"
          :disabled="authStore.loading"
        />

        <v-btn
          block
          size="large"
          color="primary"
          @click="handleLogin"
          :loading="authStore.loading"
          :disabled="!email || !password"
        >
          Sign In
        </v-btn>

        <div class="text-center mt-3">
          <router-link to="/forgot-password" class="text-primary text-body-2 text-decoration-none">
            Forgot Password?
          </router-link>
        </div>

        <div class="text-center mt-6">
          <p class="text-caption text-grey">Demo credentials:</p>
          <v-chip size="small" class="ma-1" @click="fillDemo('agency_admin@local')">Agency Admin</v-chip>
          <v-chip size="small" class="ma-1" @click="fillDemo('agency_user@local')">Agency User</v-chip>
          <v-chip size="small" class="ma-1" @click="fillDemo('owner_admin@local')">Owner Admin</v-chip>
          <v-chip size="small" class="ma-1" color="teal" @click="fillDemo('client_user@local')">Client User</v-chip>
        </div>

        <div class="text-center mt-6">
          <router-link to="/privacy" class="text-caption text-medium-emphasis text-decoration-none mr-3">
            Privacy Policy
          </router-link>
          <router-link to="/terms" class="text-caption text-medium-emphasis text-decoration-none">
            Terms of Service
          </router-link>
        </div>
      </v-card>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const showPassword = ref(false)

function fillDemo(demoEmail: string) {
  email.value = demoEmail
  password.value = 'admin123'
}

async function handleLogin() {
  if (!email.value || !password.value) return
  const success = await authStore.login(email.value, password.value)
  if (success) {
    if (authStore.isClientUser) {
      router.push('/portal')
    } else if (authStore.userRole === 'OWNER_ADMIN') {
      router.push('/owner')
    } else {
      router.push('/')
    }
  }
}
</script>
