<script setup lang="ts">
import LoginForm from '../components/auth/LoginForm.vue'
import { usePreferencesStore } from '../stores/preferences'

const preferences = usePreferencesStore()
</script>

<template>
  <main class="login-page" :aria-label="preferences.t('login.pageLabel')">
    <LoginForm />
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 32px 16px;
}
</style>
