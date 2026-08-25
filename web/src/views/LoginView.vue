<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { Eye, EyeOff, LoaderCircle, LockKeyhole, UserRound } from 'lucide-vue-next'

import { AuthRequestError, useAuthStore } from '@/stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()

const username = ref('')
const password = ref('')
const passwordVisible = ref(false)
const submitting = ref(false)
const errorCode = ref<string | null>(null)

const errorMessage = computed(() => {
  if (!errorCode.value) return ''
  const knownCode = ['INVALID_CREDENTIALS', 'ACCOUNT_LOCKED', 'ACCOUNT_UNAVAILABLE'].includes(
    errorCode.value,
  )
  return knownCode ? t(`auth.errors.${errorCode.value}`) : t('auth.errors.DEFAULT')
})

function safeRedirect() {
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  return redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/'
}

async function submit() {
  if (submitting.value) return
  submitting.value = true
  errorCode.value = null
  try {
    await authStore.login(username.value, password.value)
    await router.replace(safeRedirect())
  } catch (error) {
    errorCode.value = error instanceof AuthRequestError ? error.code : 'DEFAULT'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-screen">
    <aside class="login-brand" aria-label="Zoom Education">
      <div class="login-brand__identity">
        <span class="login-brand__mark">ZE</span>
        <span>
          <strong>{{ t('brand.name') }}</strong>
          <small>{{ t('brand.product') }}</small>
        </span>
      </div>
      <div class="login-brand__context">
        <p>{{ t('auth.contextEyebrow') }}</p>
        <strong>{{ t('auth.contextTitle') }}</strong>
        <span>{{ t('auth.contextDescription') }}</span>
      </div>
      <small class="login-brand__environment">Development · v0.1.0</small>
    </aside>

    <main class="login-main">
      <form class="login-form" novalidate @submit.prevent="submit">
        <header>
          <p class="eyebrow">{{ t('auth.eyebrow') }}</p>
          <h1>{{ t('auth.title') }}</h1>
          <p>{{ t('auth.subtitle') }}</p>
        </header>

        <div v-if="errorMessage" class="auth-error" role="alert">
          {{ errorMessage }}
        </div>

        <label class="auth-field">
          <span>{{ t('auth.username') }}</span>
          <span class="auth-input">
            <UserRound :size="18" aria-hidden="true" />
            <input
              v-model.trim="username"
              name="username"
              type="text"
              autocomplete="username"
              maxlength="64"
              required
              :placeholder="t('auth.usernamePlaceholder')"
            />
          </span>
        </label>

        <label class="auth-field">
          <span>{{ t('auth.password') }}</span>
          <span class="auth-input">
            <LockKeyhole :size="18" aria-hidden="true" />
            <input
              v-model="password"
              name="password"
              :type="passwordVisible ? 'text' : 'password'"
              autocomplete="current-password"
              maxlength="200"
              required
              :placeholder="t('auth.passwordPlaceholder')"
            />
            <button
              class="auth-input__action"
              type="button"
              :aria-label="passwordVisible ? t('auth.hidePassword') : t('auth.showPassword')"
              @click="passwordVisible = !passwordVisible"
            >
              <EyeOff v-if="passwordVisible" :size="18" />
              <Eye v-else :size="18" />
            </button>
          </span>
        </label>

        <button
          class="login-submit"
          type="submit"
          :disabled="submitting || !username || !password"
        >
          <LoaderCircle v-if="submitting" class="spinning" :size="18" aria-hidden="true" />
          <span>{{ submitting ? t('auth.signingIn') : t('auth.signIn') }}</span>
        </button>
      </form>
    </main>
  </div>
</template>
