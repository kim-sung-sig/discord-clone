<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AttachmentPreview from './AttachmentPreview.vue'
import ReactionBar from './ReactionBar.vue'
import TypingIndicator from './TypingIndicator.vue'
import { useAuthStore } from '../../stores/auth'
import { usePreferencesStore } from '../../stores/preferences'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const auth = useAuthStore()
const preferences = usePreferencesStore()
const composerReady = ref(false)
const activeChannelLabel = computed(() => {
  const activeChannel = shell.activeChannel

  if (!activeChannel) {
    return preferences.t('editor.emptyTitle')
  }

  return activeChannel.type === 'GUILD_TEXT'
    ? `# ${activeChannel.name}`
    : `${preferences.t('channel.kind.voice')} ${activeChannel.name}`
})
const composerPlaceholder = computed(() => `${preferences.t('composer.placeholder')} ${activeChannelLabel.value}`)
const activeTypingUserIds = computed(() => shell.activeTypingUserIds)
const emptyStateText = computed(() => `${preferences.t('editor.emptyBody')} ${activeChannelLabel.value}`)
const canLoadOlderMessages = computed(() => Boolean(auth.accessToken && shell.activeMessagePageCursor))

onMounted(() => {
  composerReady.value = true
})

async function submitMessage() {
  await shell.sendMessage(auth.accessToken)
}

async function loadOlderMessages() {
  if (!auth.accessToken) {
    return
  }

  await shell.loadOlderActiveChannelMessages(auth.accessToken)
}
</script>

<template>
  <section class="chat-viewport" data-testid="chat-viewport" aria-label="Messages">
    <header class="chat-header">
      <h2 data-testid="active-channel">{{ activeChannelLabel }}</h2>
      <p>{{ preferences.t('editor.channelDescription') }}</p>
    </header>
    <div class="message-list" data-testid="message-list" aria-live="polite">
      <button
        v-if="canLoadOlderMessages"
        class="load-older-messages"
        type="button"
        data-testid="load-older-messages"
        :disabled="shell.apiBusy"
        @click="loadOlderMessages"
      >
        {{ preferences.t('message.loadOlder') }}
      </button>
      <section
        v-if="shell.activeMessages.length === 0"
        class="message-empty-state"
        data-testid="message-empty-state"
        role="status"
      >
        <strong>{{ preferences.t('editor.emptyTitle') }}</strong>
        <span>{{ emptyStateText }}</span>
      </section>
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
              {{ preferences.t('message.pinned') }}
            </span>
            <span
              v-if="message.edited && !message.deleted"
              class="message-edited"
              data-testid="message-edited-marker"
            >
              {{ preferences.t('message.edited') }}
            </span>
          </div>
          <p v-if="message.deleted" class="message-tombstone" data-testid="message-tombstone">
            {{ preferences.t('message.deleted') }}
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
            <div v-if="message.attachments.length" class="message-attachments" aria-label="Attachments">
              <article
                v-for="attachment in message.attachments"
                :key="`${message.id}-${attachment.id}`"
                class="message-attachment"
                :data-testid="`message-attachment-${attachment.id}`"
              >
                <span>{{ attachment.filename }}</span>
                <small>{{ attachment.contentType }}</small>
              </article>
            </div>
            <ReactionBar :message-id="message.id" />
          </template>
        </div>
      </article>
    </div>
    <TypingIndicator :user-names="activeTypingUserIds" />
    <form class="message-composer" data-testid="message-composer" @submit.prevent="submitMessage">
      <AttachmentPreview
        v-if="shell.stagedAttachment"
        :attachment="shell.stagedAttachment"
        removable
        @remove="shell.clearStagedAttachment"
      />
      <button
        class="attachment-stage-button"
        type="button"
        data-testid="attachment-stage-demo"
        :disabled="!composerReady"
        @click="shell.stageDemoAttachment"
      >
        {{ preferences.t('composer.attachImage') }}
      </button>
      <input
        v-model="shell.composerBody"
        data-testid="message-input"
        :placeholder="composerPlaceholder"
        :disabled="!composerReady"
        aria-label="Message composer"
      >
      <button type="submit" data-testid="message-send" :disabled="!composerReady">
        {{ preferences.t('composer.send') }}
      </button>
    </form>
  </section>
</template>
