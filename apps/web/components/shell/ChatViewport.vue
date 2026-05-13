<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import TypingIndicator from './TypingIndicator.vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const composerReady = ref(false)
const activeChannelLabel = computed(() => {
  const activeChannel = shell.activeChannel

  if (!activeChannel) {
    return 'No active channel'
  }

  return activeChannel.type === 'GUILD_TEXT'
    ? `# ${activeChannel.name}`
    : `Voice ${activeChannel.name}`
})
const composerPlaceholder = computed(() => `Message ${activeChannelLabel.value}`)
const activeTypingUserIds = computed(() => shell.activeTypingUserIds)

onMounted(() => {
  composerReady.value = true
})
</script>

<template>
  <section class="chat-viewport" data-testid="chat-viewport" aria-label="Messages">
    <header class="chat-header">
      <h2 data-testid="active-channel">{{ activeChannelLabel }}</h2>
      <p>Enterprise Discord clone bootstrap channel.</p>
    </header>
    <div class="message-list" data-testid="message-list" aria-live="polite">
      <article
        v-for="message in shell.activeMessages"
        :key="message.id"
        class="message"
        :class="{ 'message-deleted': message.deleted }"
      >
        <div class="avatar">{{ message.author.slice(0, 2).toUpperCase() }}</div>
        <div class="message-card" data-testid="message-card">
          <div class="message-meta">
            <strong>{{ message.author }}</strong>
            <time :datetime="message.createdAt">{{ new Date(message.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) }}</time>
            <span
              v-if="message.pinned && !message.deleted"
              class="message-pill"
              data-testid="message-pinned-label"
            >
              Pinned
            </span>
            <span
              v-if="message.edited && !message.deleted"
              class="message-edited"
              data-testid="message-edited-marker"
            >
              edited
            </span>
          </div>
          <p v-if="message.deleted" class="message-tombstone" data-testid="message-tombstone">
            message deleted
          </p>
          <template v-else>
            <p>{{ message.body }}</p>
            <div v-if="message.mentions.length" class="mention-chips" aria-label="Mentions">
              <span
                v-for="mention in message.mentions"
                :key="`${message.id}-${mention}`"
                class="mention-chip"
                :data-testid="`mention-chip-${mention}`"
              >
                @{{ mention }}
              </span>
            </div>
          </template>
        </div>
      </article>
    </div>
    <TypingIndicator :user-names="activeTypingUserIds" />
    <form class="message-composer" data-testid="message-composer" @submit.prevent="shell.sendMessage">
      <input
        v-model="shell.composerBody"
        data-testid="message-input"
        :placeholder="composerPlaceholder"
        :disabled="!composerReady"
        aria-label="Message composer"
      >
      <button type="submit" data-testid="message-send" :disabled="!composerReady">Send</button>
    </form>
  </section>
</template>
