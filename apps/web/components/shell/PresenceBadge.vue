<script setup lang="ts">
import { computed } from 'vue'
import type { ShellPresenceStatus } from '../../stores/shell'

const props = defineProps<{
  userId: string
  status: ShellPresenceStatus
}>()

const statusLabel = computed(() => {
  switch (props.status) {
    case 'ONLINE':
      return 'Online'
    case 'IDLE':
      return 'Idle'
    case 'DO_NOT_DISTURB':
      return 'Do not disturb'
    case 'OFFLINE':
      return 'Offline'
  }
})
</script>

<template>
  <span
    class="presence-badge"
    :class="`presence-badge-${status.toLowerCase().replaceAll('_', '-')}`"
    :data-testid="`presence-badge-${userId}`"
    :aria-label="`${userId} presence: ${statusLabel}`"
  >
    <span class="presence-dot" aria-hidden="true" />
    <span>{{ statusLabel }}</span>
  </span>
</template>
