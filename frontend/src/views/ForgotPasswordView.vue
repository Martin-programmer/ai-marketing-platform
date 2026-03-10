<template>
  <v-app>
    <v-main class="d-flex align-center justify-center" style="min-height: 100vh; background: linear-gradient(135deg, #1565C0 0%, #0D47A1 100%);">
      <v-card width="420" class="pa-8" elevation="12" rounded="lg">
        <div class="text-center mb-6">
          <v-icon size="48" color="primary" class="mb-2">mdi-lock-reset</v-icon>
          <h1 class="text-h5 font-weight-bold">Forgot Password</h1>
          <p class="text-body-2 text-grey mt-1">Enter your email to receive a reset link</p>
        </div>

        <!-- Success state -->
        <div v-if="submitted" class="text-center py-4">
          <v-icon size="64" color="success" class="mb-4">mdi-email-check</v-icon>
          <h2 class="text-h6 mb-2">Check Your Email</h2>
          <p class="text-body-2 text-grey mb-4">
            If this email is registered, you will receive a password reset link shortly.
          </p>
          <v-btn color="primary" to="/login" prepend-icon="mdi-arrow-left">
            Back to Login
          </v-btn>
        </div>

        <!-- Form -->
        <div v-else>
          <v-alert v-if="error" type="error" variant="tonal" class="mb-4" closable @click:close="error = null">
            {{ error }}
          </v-alert>

          <v-form @submit.prevent="handleSubmit">
            <v-text-field
              v-model="email"
              label="Email"
              type="email"
              prepend-inner-icon="mdi-email-outline"
              variant="outlined"
              density="comfortable"
              class="mb-4"
              :rules="[v => !!v || 'Required', v => /.+@.+\..+/.test(v) || 'Invalid email']"
              :disabled="loading"
              @keyup.enter="handleSubmit"
            />

            <v-btn
              block
              size="large"
              color="primary"
              type="submit"
              :loading="loading"
              :disabled="!email"
            >
              Send Reset Link
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
import { ref } from 'vue'
import api from '@/api/client'

const email = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const submitted = ref(false)

async function handleSubmit() {
  if (!email.value) return
  loading.value = true
  error.value = null

  try {
    await api.post('/auth/forgot-password', { email: email.value })
    submitted.value = true
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Something went wrong. Please try again.'
  } finally {
    loading.value = false
  }
}
</script>
