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
          <v-icon v-else size="48" color="primary" class="mb-2">mdi-rocket-launch</v-icon>
          <h1 class="text-h5 font-weight-bold">{{ brandingStore.name }}</h1>
          <p v-if="brandingStore.tagline && !authStore.twoFactorPending" class="text-body-2 text-grey mt-1">
            {{ brandingStore.tagline }}
          </p>
          <p v-else class="text-body-2 text-grey mt-1">
            {{ authStore.twoFactorPending ? 'Enter verification code' : 'Sign in to your account' }}
          </p>
        </div>

        <v-alert v-if="authStore.error" type="error" variant="tonal" class="mb-4" closable @click:close="authStore.error = null">
          {{ authStore.error }}
        </v-alert>

        <v-alert v-if="agencyNotFound" type="error" variant="tonal" class="mb-4">
          Agency not found.
          <template #append>
            <v-btn variant="text" size="small" to="/login">Normal Login</v-btn>
          </template>
        </v-alert>

        <!-- ── Login Form ── -->
        <template v-if="!authStore.twoFactorPending">
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
            class="mb-1"
            @keyup.enter="handleLogin"
            :disabled="authStore.loading"
          />

          <v-checkbox
            v-model="rememberMe"
            label="Remember me"
            density="compact"
            hide-details
            class="mb-3"
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
        </template>

        <!-- ── 2FA Verification ── -->
        <template v-else>
          <p class="text-body-2 text-center mb-1">
            We sent a 6-digit code to
          </p>
          <p class="text-body-2 text-center font-weight-medium mb-5">
            {{ authStore.twoFactorEmail }}
          </p>

          <VerificationCodeInput
            ref="codeInputRef"
            :disabled="authStore.loading"
            @complete="handleVerify"
          />

          <v-btn
            block
            size="large"
            color="primary"
            class="mt-5"
            @click="handleVerify(verificationCode)"
            :loading="authStore.loading"
            :disabled="verificationCode.length < 6"
          >
            Verify
          </v-btn>

          <div class="text-center mt-4">
            <v-btn
              v-if="!authStore.twoFactorResendLimitReached"
              variant="text"
              size="small"
              :disabled="resendCooldown > 0 || authStore.loading"
              @click="handleResend"
            >
              {{ resendCooldown > 0 ? `Resend code (${resendCooldown}s)` : 'Resend code' }}
            </v-btn>
            <p v-else class="text-body-2 text-medium-emphasis">
              Please login again to get a new code.
            </p>
          </div>

          <div class="text-center mt-1">
            <v-btn variant="text" size="small" @click="handleBackToLogin">
              <v-icon start size="small">mdi-arrow-left</v-icon>
              Back to login
            </v-btn>
          </div>
        </template>

        <div v-if="showDemoCredentials && !authStore.twoFactorPending" class="text-center mt-6">
          <p class="text-caption text-grey">Demo credentials:</p>
          <v-chip size="small" class="ma-1" @click="fillDemo('agency_admin@local')">Agency Admin</v-chip>
          <v-chip size="small" class="ma-1" @click="fillDemo('agency_user@local')">Agency User</v-chip>
          <v-chip size="small" class="ma-1" @click="fillDemo('owner_admin@local')">Owner Admin</v-chip>
          <v-chip size="small" class="ma-1" color="teal" @click="fillDemo('client_user@local')">Client User</v-chip>
        </div>

        <div class="text-center mt-6">
          <div
            v-if="brandingStore.poweredByVisible && brandingStore.poweredByText"
            class="text-caption text-medium-emphasis mb-3"
          >
            Powered by {{ brandingStore.poweredByText }}
          </div>
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
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useBrandingStore } from '@/stores/branding'
import VerificationCodeInput from '@/components/VerificationCodeInput.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const brandingStore = useBrandingStore()
const agencyNotFound = ref(false)

onMounted(async () => {
  const agencySlug = typeof route.params.agencySlug === 'string' ? route.params.agencySlug : undefined
  const agencyId = typeof route.query.agency === 'string' ? route.query.agency : undefined

  agencyNotFound.value = false

  if (agencySlug) {
    const found = await brandingStore.fetchPublicBrandingBySlug(agencySlug)
    agencyNotFound.value = !found
    return
  }

  await brandingStore.fetchPublicBranding(agencyId || undefined)
})

const email = ref('')
const password = ref('')
const showPassword = ref(false)
const rememberMe = ref(false)
const showDemoCredentials = window.location.hostname.includes('localhost')

// 2FA state
const verificationCode = ref('')
const resendCooldown = ref(0)
const codeInputRef = ref<InstanceType<typeof VerificationCodeInput> | null>(null)
let cooldownTimer: number | null = null

function fillDemo(demoEmail: string) {
  email.value = demoEmail
  password.value = 'admin123'
}

async function handleLogin() {
  if (!email.value || !password.value) return
  const result = await authStore.login(email.value, password.value, rememberMe.value)
  if (result.success) {
    navigateAfterLogin()
  } else if (result.requiresTwoFactor) {
    startResendCooldown(result.resendCooldownSeconds ?? 60)
  }
}

async function handleVerify(code: string) {
  verificationCode.value = code
  if (code.length < 6) return
  const ok = await authStore.verify2fa(code)
  if (ok) {
    navigateAfterLogin()
  } else {
    codeInputRef.value?.clear()
    verificationCode.value = ''
  }
}

async function handleResend() {
  const result = await authStore.resend2fa()
  if (result.success) {
    startResendCooldown(result.resendCooldownSeconds ?? 60)
    codeInputRef.value?.clear()
    verificationCode.value = ''
  }
}

function handleBackToLogin() {
  authStore.cancelTwoFactor()
  clearCooldownTimer()
  verificationCode.value = ''
}

function navigateAfterLogin() {
  if (authStore.isClientUser) {
    router.push('/portal')
  } else if (authStore.userRole === 'OWNER_ADMIN') {
    router.push('/owner')
  } else {
    router.push('/')
  }
}

function startResendCooldown(seconds = 60) {
  clearCooldownTimer()
  resendCooldown.value = seconds
  cooldownTimer = window.setInterval(() => {
    resendCooldown.value--
    if (resendCooldown.value <= 0) {
      clearCooldownTimer()
    }
  }, 1000)
}

function clearCooldownTimer() {
  if (cooldownTimer !== null) {
    window.clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  resendCooldown.value = 0
}

onUnmounted(() => {
  clearCooldownTimer()
})
</script>
