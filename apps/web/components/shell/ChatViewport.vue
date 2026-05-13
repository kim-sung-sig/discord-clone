<script setup lang="ts">
import { computed } from 'vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const activeChannelLabel = computed(() => {
  const activeChannel = shell.activeChannel

  if (!activeChannel) {
    return 'No active channel'
  }

  return activeChannel.type === 'GUILD_TEXT'
    ? `# ${activeChannel.name}`
    : `Voice ${activeChannel.name}`
})
</script>

<template>
  <section class="chat-viewport" data-testid="chat-viewport" aria-label="Messages">
    <header class="chat-header">
      <h2 data-testid="active-channel">{{ activeChannelLabel }}</h2>
      <p>Enterprise Discord clone bootstrap channel.</p>
    </header>
    <article
      v-for="message in shell.activeMessages"
      :key="message.author + message.body"
      class="message"
    >
      <div class="avatar">{{ message.author.slice(0, 2).toUpperCase() }}</div>
      <div class="message-card">
        <strong>{{ message.author }}</strong>
        <p>{{ message.body }}</p>
      </div>
    </article>
  </section>
</template>
