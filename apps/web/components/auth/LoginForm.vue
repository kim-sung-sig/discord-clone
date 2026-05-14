<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const email = ref('')
const password = ref('')
const isHydrated = ref(false)

const hasAccessToken = computed(() => Boolean(auth.accessToken))

onMounted(() => {
  isHydrated.value = true
})

async function submitLogin() {
  await auth.login({
    email: email.value,
    password: password.value
  })
}
</script>

<template>
  <section class="login-card" aria-labelledby="login-title">
    <p class="login-kicker">Welcome back</p>
    <h1 id="login-title">Sign in to Discord Clone</h1>
    <p class="login-copy">
      Sign in through the Spring Boot API. Your access token stays in memory only.
    </p>

    <form class="login-form" data-testid="login-form" @submit.prevent="submitLogin">
      <p
        v-if="!isHydrated"
        id="login-hydration-status"
        class="login-loading"
        role="status"
      >
        Sign-in controls will be enabled after the page finishes loading.
      </p>

      <label class="login-field" for="login-email">
        Email
        <input
          id="login-email"
          v-model="email"
          data-testid="login-email"
          name="email"
          type="email"
          autocomplete="email"
          :disabled="!isHydrated"
          :aria-describedby="!isHydrated ? 'login-hydration-status' : undefined"
          required
        >
      </label>

      <label class="login-field" for="login-password">
        Password
        <input
          id="login-password"
          v-model="password"
          data-testid="login-password"
          name="password"
          type="password"
          autocomplete="current-password"
          :disabled="!isHydrated"
          :aria-describedby="!isHydrated ? 'login-hydration-status' : undefined"
          required
        >
      </label>

      <p v-if="auth.error" class="login-error" data-testid="login-error" role="alert">
        {{ auth.error }}
      </p>

      <p v-if="hasAccessToken" class="login-success" data-testid="login-success" role="status">
        Signed in with backend session.
      </p>

      <button
        class="login-submit"
        data-testid="login-submit"
        type="submit"
        :disabled="!isHydrated || auth.isLoading"
        :aria-describedby="!isHydrated ? 'login-hydration-status' : undefined"
      >
        {{ auth.isLoading ? 'Signing in...' : 'Sign in' }}
      </button>
    </form>

    <p class="login-token-policy" data-testid="login-token-policy">
      Access token is stored in memory for this frontend slice.
    </p>
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

.login-copy,
.login-token-policy {
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

.login-error,
.login-success,
.login-loading {
  margin: 0;
  padding: 12px 14px;
  border-radius: 14px;
}

.login-error {
  color: #ffe3e3;
  background: rgba(255, 107, 107, 0.16);
  border: 1px solid rgba(255, 107, 107, 0.28);
}

.login-success {
  color: #d8ffea;
  background: rgba(57, 217, 138, 0.16);
  border: 1px solid rgba(57, 217, 138, 0.32);
}

.login-loading {
  color: #dbeafe;
  background: rgba(84, 166, 255, 0.14);
  border: 1px solid rgba(84, 166, 255, 0.28);
}

.login-submit {
  padding: 14px 18px;
  color: #06131d;
  font-weight: 900;
  cursor: pointer;
  background: linear-gradient(145deg, var(--accent), #54a6ff);
  border: 0;
  border-radius: 16px;
}

.login-field input:disabled,
.login-submit:disabled {
  cursor: wait;
  opacity: 0.6;
}
</style>
