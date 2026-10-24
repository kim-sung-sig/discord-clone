<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useShellStore } from '../../stores/shell'
import { useAuthStore } from '../../stores/auth'

const shell = useShellStore()
const auth = useAuthStore()
// The client middleware restores the session before hydration, but the server render has none.
// Auth-dependent output waits for mount so the first client render matches the server HTML.
const isHydrated = ref(false)
const identityName = computed(() =>
  isHydrated.value ? auth.user?.displayName ?? shell.currentUser : shell.currentUser
)

onMounted(() => {
  isHydrated.value = true
})
</script>

<template>
  <footer class="user-panel" data-testid="user-panel" aria-label="Current user">
    <div class="user-panel-identity">
      <strong>{{ identityName }}</strong>
      <span class="voice-state">{{ shell.voiceState }}</span>
    </div>
    <section v-if="isHydrated && auth.accessToken" class="session-panel" data-testid="session-panel" aria-label="Signed-in sessions">
      <button type="button" class="session-action" data-testid="load-sessions" @click="auth.loadSessions()">
        Sessions
      </button>
      <button type="button" class="session-action" data-testid="logout-session" @click="auth.logout()">
        Logout
      </button>
      <p v-if="auth.sessionError" class="session-error" data-testid="session-error" role="alert">
        {{ auth.sessionError }}
      </p>
      <ul v-if="auth.sessions.length" class="session-list" data-testid="session-list">
        <li v-for="session in auth.sessions" :key="session.id" :data-testid="`session-${session.id}`">
          <span>{{ session.deviceName }}</span>
          <button
            type="button"
            class="session-revoke"
            :disabled="session.revoked"
            :data-testid="`revoke-session-${session.id}`"
            @click="auth.revokeSession(session.id)"
          >
            {{ session.revoked ? 'Revoked' : 'Revoke' }}
          </button>
        </li>
      </ul>
    </section>
  </footer>
</template>
