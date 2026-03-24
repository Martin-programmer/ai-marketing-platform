<template>
  <v-app>
    <v-main class="d-flex align-center justify-center" :style="{ minHeight: '100vh', background: brandingStore.gradientStyle }">
      <v-card width="420" class="pa-8" elevation="12" rounded="lg">
        <div class="text-center mb-6">
          <img
            v-if="brandingStore.logoUrl"
            :src="brandingStore.logoUrl"
            :alt="brandingStore.name"
            style="max-width: 200px; max-height: 56px; object-fit: contain;"
            class="mb-2"
          />
          <v-icon v-else size="48" color="primary" class="mb-2">mdi-lock-reset</v-icon>
          <h1 class="text-h5 font-weight-bold">Forgot Password</h1>
          <p class="text-body-2 text-grey mt-1">Enter your email to receive a reset link</p>
        </div>

        <div>
          <v-alert v-if="submitted" type="success" variant="tonal" class="mb-4">
            If this email is registered, you'll receive a reset link.
          </v-alert>

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
              :rules="[(v: string) => !!v || 'Required', (v: string) => /.+@.+\..+/.test(v) || 'Invalid email']"
              :disabled="loading"
              @keyup.enter="handleSubmit"
            />

            <v-btn
              block
              size="large"
              color="primary"
              type="submit"
              :loading="loading"
              :disabled="!email || cooldown > 0"
            >
              {{ cooldown > 0 ? `Send Again in ${cooldown}s` : 'Send Reset Link' }}
            </v-btn>
          </v-form>

          <p v-if="cooldown > 0" class="text-caption text-medium-emphasis text-center mt-3">
            You can request again in {{ cooldown }}s
          </p>

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
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import api from '@/api/client'
import { useBrandingStore } from '@/stores/branding'

const route = useRoute()
const brandingStore = useBrandingStore()

onMounted(() => {
  const agencyId = route.query.agency as string | undefined
  brandingStore.fetchPublicBranding(agencyId || undefined)
})

const email = ref('')
const loading = ref(false)
const error = ref<string | null>(null)
const submitted = ref(false)
const cooldown = ref(0)
let cooldownTimer: number | null = null

function startCooldown(seconds = 60) {
  clearCooldown()
  cooldown.value = seconds
  cooldownTimer = window.setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      clearCooldown()
    }
  }, 1000)
}

function clearCooldown() {
  if (cooldownTimer !== null) {
    window.clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  if (cooldown.value < 0) {
    cooldown.value = 0
  }
}

async function handleSubmit() {
  if (!email.value || cooldown.value > 0) return
  loading.value = true
  error.value = null

  try {
    const res = await api.post<{ cooldownSeconds?: number }>('/auth/forgot-password', { email: email.value })
    submitted.value = true
    startCooldown(res.data.cooldownSeconds ?? 60)
  } catch (e: any) {
    error.value = e.response?.data?.message || 'Something went wrong. Please try again.'
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  clearCooldown()
})
</script>
