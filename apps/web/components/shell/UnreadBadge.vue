<script setup lang="ts">
withDefaults(
  defineProps<{
    count: number
    targetId: string
    label?: string
  }>(),
  {
    label: 'unread'
  }
)
</script>

<template>
  <span
    v-if="count > 0"
    class="unread-badge"
    :data-testid="`unread-badge-${targetId}`"
    :aria-label="`${count} ${label}`"
  >
    {{ count }} {{ label }}
  </span>
</template>
