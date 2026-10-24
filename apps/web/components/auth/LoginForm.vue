<script setup lang="ts">
import { navigateTo } from '#app'
import { onMounted, ref } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { usePreferencesStore } from '../../stores/preferences'

const workspacePath = '/'

const auth = useAuthStore()
const preferences = usePreferencesStore()
const email = ref('')
const password = ref('')
const isHydrated = ref(false)

const openWorkspace = () => navigateTo(workspacePath, { replace: true })

onMounted(() => {
  preferences.hydrate()
  void auth.restoreSession().then(async (restored) => {
    if (restored) {
      await openWorkspace()
    }
  }).finally(() => {
    isHydrated.value = true
  })
})

async function submitLogin() {
  await auth.login({
    email: email.value,
    password: password.value
  })
  if (auth.accessToken) {
    await openWorkspace()
  }
}
</script>

<template>
  <section class="login-card" aria-labelledby="login-title">
    <p class="login-kicker">{{ preferences.t('login.kicker') }}</p>
    <h1 id="login-title">{{ preferences.t('login.title') }}</h1>
    <p class="login-copy">{{ preferences.t('login.description') }}</p>

    <form class="login-form" data-testid="login-form" @submit.prevent="submitLogin">
      <label class="login-field" for="login-email">
        {{ preferences.t('login.email') }}
        <input
          id="login-email"
          v-model="email"
          data-testid="login-email"
          name="email"
          type="email"
          autocomplete="email"
          required
        >
      </label>

      <label class="login-field" for="login-password">
        {{ preferences.t('login.password') }}
        <input
          id="login-password"
          v-model="password"
          data-testid="login-password"
          name="password"
          type="password"
          autocomplete="current-password"
          required
        >
      </label>

      <p v-if="auth.error" class="login-error" data-testid="login-error" role="alert">
        {{ auth.error }}
      </p>

      <button
        class="login-submit"
        data-testid="login-submit"
        type="submit"
        :disabled="!isHydrated || auth.isLoading"
      >
        {{ auth.isLoading ? preferences.t('login.submitting') : preferences.t('login.submit') }}
      </button>
    </form>
  </section>
</template>

<style scoped>
.login-card {
  width: min(100%, 440px);
  padding: 34px;
  background: rgba(23, 32, 51, 0.9);
  border: 1px solid var(--line);
  border-radius: 28px;
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.32);
}

.login-kicker {
  margin: 0 0 10px;
  color: var(--accent);
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.login-card h1 {
  margin: 0;
  font-size: clamp(30px, 7vw, 44px);
  line-height: 1;
}

.login-copy {
  color: var(--muted);
  line-height: 1.6;
}

.login-form {
  display: grid;
  gap: 16px;
  margin-top: 26px;
}

.login-field {
  display: grid;
  gap: 8px;
  color: var(--muted);
  font-weight: 700;
}

.login-field input {
  width: 100%;
  padding: 14px 16px;
  color: var(--ink);
  background: rgba(15, 23, 42, 0.88);
  border: 1px solid var(--line);
  border-radius: 14px;
  outline: none;
}

.login-field input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 4px rgba(57, 217, 138, 0.14);
}

.login-error {
  margin: 0;
  padding: 12px 14px;
  border-radius: 14px;
}

.login-error {
  color: #ffe3e3;
  background: rgba(255, 107, 107, 0.16);
  border: 1px solid rgba(255, 107, 107, 0.28);
}

.login-submit {
  display: inline-flex;
  justify-content: center;
  padding: 14px 18px;
  color: #06131d;
  font-weight: 900;
  cursor: pointer;
  background: linear-gradient(145deg, var(--accent), #54a6ff);
  border: 0;
  border-radius: 16px;
  text-decoration: none;
}

.login-field input:disabled,
.login-submit:disabled {
  cursor: wait;
  opacity: 0.6;
}
</style>
