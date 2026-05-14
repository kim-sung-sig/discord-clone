<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const isHydrated = ref(false)

onMounted(() => {
  isHydrated.value = true
})
</script>

<template>
  <section class="thread-list" data-testid="thread-list" aria-label="Forum threads">
    <button
      v-for="thread in shell.activeForumThreads"
      :key="thread.id"
      class="thread-row"
      type="button"
      :data-testid="thread.id"
      :disabled="!isHydrated"
      :aria-current="thread.id === shell.forum.activeThreadId ? 'page' : undefined"
      @click="shell.selectThread(thread.id)"
    >
      <span class="thread-row-title">{{ thread.title }}</span>
      <strong>{{ thread.type === 'PUBLIC' ? 'Public' : 'Private' }}</strong>
      <small :data-testid="`thread-status-${thread.id}`">
        {{ thread.archived ? 'Archived' : 'Open' }}
      </small>
    </button>
  </section>
</template>
