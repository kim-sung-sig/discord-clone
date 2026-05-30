<script setup lang="ts">
import PresenceBadge from './PresenceBadge.vue'
import { usePreferencesStore } from '../../stores/preferences'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const preferences = usePreferencesStore()
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
  </aside>
</template>
