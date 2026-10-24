<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PresenceBadge from './PresenceBadge.vue'
import { useAuthStore } from '../../stores/auth'
import { usePreferencesStore } from '../../stores/preferences'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const auth = useAuthStore()
const preferences = usePreferencesStore()
const isHydrated = ref(false)

// The server render has no session, so the signed-in fallback appears only after mount.
const signedInName = computed(() =>
  isHydrated.value && shell.members.length === 0 && auth.user
    ? auth.user.displayName || auth.user.username
    : ''
)

onMounted(() => {
  isHydrated.value = true
})
</script>

<template>
  <aside class="member-sidebar" data-testid="member-sidebar" aria-label="Members">
    <h2>{{ preferences.t('panel.members') }}</h2>
    <div
      v-for="member in shell.members"
      :key="member.name"
      class="member"
    >
      <span class="status-dot" aria-hidden="true" />
      <span>{{ member.name }}</span>
      <small>{{ member.status }}</small>
      <PresenceBadge
        :user-id="member.name"
        :status="shell.presenceStatusForUser(member.name)"
      />
    </div>
    <div
      v-if="signedInName"
      class="member"
      data-testid="member-self"
    >
      <span class="status-dot" aria-hidden="true" />
      <span>{{ signedInName }}</span>
      <PresenceBadge
        :user-id="signedInName"
        status="ONLINE"
      />
    </div>
  </aside>
</template>
