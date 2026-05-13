<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  userNames: string[]
}>()

const typingLabel = computed(() => {
  if (props.userNames.length === 1) {
    return `${props.userNames[0]} is typing`
  }

  if (props.userNames.length === 2) {
    return `${props.userNames[0]} and ${props.userNames[1]} are typing`
  }

  return `${props.userNames[0]}, ${props.userNames[1]}, and ${props.userNames.length - 2} others are typing`
})
</script>

<template>
  <p
    v-if="userNames.length"
    class="typing-indicator"
    data-testid="typing-indicator"
    role="status"
    aria-live="polite"
  >
    <span aria-hidden="true">...</span>
    {{ typingLabel }}
  </p>
</template>
