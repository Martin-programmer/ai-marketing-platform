<template>
  <div class="d-flex justify-center ga-2">
    <v-text-field
      v-for="(_, i) in digits"
      :key="i"
      :ref="(el: any) => setInputRef(el, i)"
      v-model="digits[i]"
      variant="outlined"
      density="comfortable"
      maxlength="1"
      class="verification-digit"
      hide-details
      :disabled="disabled"
      inputmode="numeric"
      autocomplete="one-time-code"
      @input="onInput(i)"
      @keydown="onKeydown($event, i)"
      @paste="onPaste"
      @focus="onFocus(i)"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch, type ComponentPublicInstance } from 'vue'

const props = withDefaults(defineProps<{
  length?: number
  disabled?: boolean
}>(), {
  length: 6,
  disabled: false
})

const emit = defineEmits<{
  complete: [code: string]
  update: [code: string]
}>()

const digits = ref<string[]>(Array.from({ length: props.length }, () => ''))
const inputRefs: (HTMLInputElement | null)[] = []

function setInputRef(el: Element | ComponentPublicInstance | null, index: number) {
  if (!el) {
    inputRefs[index] = null
    return
  }
  // Vuetify v-text-field wraps an <input>; drill into it
  const component = el as ComponentPublicInstance
  const root = component.$el as HTMLElement | undefined
  inputRefs[index] = root?.querySelector('input') ?? null
}

function getCode(): string {
  return digits.value.join('')
}

function focusIndex(i: number) {
  inputRefs[i]?.focus()
}

function onInput(i: number) {
  const val = digits.value[i]
  // Allow only digits
  if (val && !/^\d$/.test(val)) {
    digits.value[i] = ''
    return
  }
  emit('update', getCode())
  if (val && i < props.length - 1) {
    focusIndex(i + 1)
  }
  if (getCode().length === props.length) {
    emit('complete', getCode())
  }
}

function onKeydown(e: KeyboardEvent, i: number) {
  if (e.key === 'Backspace') {
    if (!digits.value[i] && i > 0) {
      digits.value[i - 1] = ''
      focusIndex(i - 1)
      e.preventDefault()
    }
  } else if (e.key === 'ArrowLeft' && i > 0) {
    focusIndex(i - 1)
  } else if (e.key === 'ArrowRight' && i < props.length - 1) {
    focusIndex(i + 1)
  }
}

function onPaste(e: ClipboardEvent) {
  e.preventDefault()
  const pasted = (e.clipboardData?.getData('text') || '').replace(/\D/g, '').slice(0, props.length)
  if (!pasted) return
  for (let i = 0; i < props.length; i++) {
    digits.value[i] = pasted[i] || ''
  }
  emit('update', getCode())
  // Focus last filled or the first empty
  const nextEmpty = digits.value.findIndex(d => !d)
  focusIndex(nextEmpty >= 0 ? nextEmpty : props.length - 1)
  if (getCode().length === props.length) {
    emit('complete', getCode())
  }
}

function onFocus(i: number) {
  inputRefs[i]?.select()
}

function clear() {
  digits.value = Array.from({ length: props.length }, () => '')
  focusIndex(0)
}

function focus() {
  focusIndex(0)
}

watch(() => props.length, (len) => {
  digits.value = Array.from({ length: len }, () => '')
})

defineExpose({ clear, focus })
</script>

<style scoped>
.verification-digit {
  max-width: 52px;
}
.verification-digit :deep(input) {
  text-align: center;
  font-size: 1.5rem;
  font-weight: 600;
  letter-spacing: 0;
  font-variant-numeric: tabular-nums;
}
</style>
