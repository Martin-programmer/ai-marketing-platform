<template>
  <v-app>
    <v-main class="d-flex align-center justify-center" style="min-height: 100vh; background: linear-gradient(135deg, #1565C0 0%, #0D47A1 100%);">
      <v-card width="420" class="pa-8" elevation="12" rounded="lg">
        <div class="text-center mb-6">
          <v-icon size="48" color="primary" class="mb-2">mdi-lock-open-variant</v-icon>
          <h1 class="text-h5 font-weight-bold">Reset Password</h1>
          <p class="text-body-2 text-grey mt-1">Enter your new password</p>
        </div>

        <!-- No token -->
        <v-alert v-if="!token" type="error" variant="tonal" class="mb-4">
          No reset token provided. Please use the link from your email.
          <template v-slot:append>
            <v-btn variant="text" size="small" to="/login">Go to Login</v-btn>
          </template>
        </v-alert>

        <!-- Success state -->
        <div v-else-if="success" class="text-center py-4">
          <v-icon size="64" color="success" class="mb-4">mdi-check-circle</v-icon>
          <h2 class="text-h6 mb-2">Password Reset Successfully!</h2>
          <p class="text-body-2 text-grey mb-4">
            Your password has been updated. You can now log in with your new password.
          </p>
          <v-btn color="primary" to="/login" prepend-icon="mdi-login">
            Go to Login
          </v-btn>
        </div>

        <!-- Form -->
        <div v-else>
          <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
            {{ error }}
          </v-alert>

          <v-form ref="formRef" v-model="formValid" @submit.prevent="handleSubmit">
            <v-text-field
              v-model="newPassword"
              :type="showPassword ? 'text' : 'password'"
              label="New Password"
              prepend-inner-icon="mdi-lock-outline"
              :append-inner-icon="showPassword ? 'mdi-eye-off' : 'mdi-eye'"
              @click:append-inner="showPassword = !showPassword"
              variant="outlined"
              density="comfortable"
              class="mb-2"
              :rules="[v => !!v || 'Required', v => (v && v.length >= 6) || 'Min 6 characters']"
              :disabled="loading"
            />

            <v-text-field
              v-model="confirmPassword"
              :type="showPassword ? 'text' : 'password'"
              label="Confirm Password"
              prepend-inner-icon="mdi-lock-check-outline"
              variant="outlined"
              density="comfortable"
              class="mb-4"
              :rules="[v => !!v || 'Required', v => v === newPassword || 'Passwords do not match']"
              :disabled="loading"
            />

            <v-btn
              block
              size="large"
              color="primary"
              type="submit"
              :loading="loading"
              :disabled="!formValid"
            >
              Reset Password
            </v-btn>
          </v-form>

          <div class="text-center mt-4">
            <router-link to="/login" class="text-primary text-decoration-none">
              ← Back to Login
            </router-link>
          </div>
        </div>
      </v-card>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/client'

const route = useRoute()

const token = computed(() => route.query.token as string)
const newPassword = ref('')
const confirmPassword = ref('')
const showPassword = ref(false)
const formValid = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
const success = ref(false)
const formRef = ref()

async function handleSubmit() {
  if (!formValid.value || !token.value) return
  loading.value = true
  error.value = null

  try {
    await api.post('/auth/reset-password', {
      token: token.value,
      newPassword: newPassword.value
    })
    success.value = true
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Failed to reset password. The link may have expired.'
  } finally {
    loading.value = false
  }
}
</script>
