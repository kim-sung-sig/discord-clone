<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const isHydrated = ref(false)

const localStateLabel = computed(() => {
  const participant = shell.activeVoiceParticipant
  if (!participant) {
    return 'Disconnected'
  }

  const flags = [
    participant.muted ? 'Muted' : 'Unmuted',
    participant.deafened ? 'Deafened' : 'Can hear',
    participant.speaking ? 'Speaking' : 'Not speaking',
    participant.screenSharing ? 'Screen sharing' : 'Screen idle'
  ]
  return flags.join(' / ')
})

onMounted(() => {
  isHydrated.value = true
})
</script>

<template>
  <aside class="voice-panel" data-testid="voice-panel" aria-label="Voice">
    <header class="voice-panel-header">
      <p>Voice</p>
      <h2>SFU skeleton</h2>
      <small data-testid="voice-token-provider">Provider {{ shell.voice.tokenProvider }}</small>
    </header>

    <section class="voice-card">
      <p>Guild voice channel</p>
      <button
        type="button"
        data-testid="voice-join-channel-war-room"
        :disabled="!isHydrated"
        @click="shell.joinVoiceChannel('channel-war-room')"
      >
        Join war-room
      </button>
      <button
        type="button"
        data-testid="voice-leave"
        :disabled="!isHydrated || !shell.voice.activeChannelId"
        @click="shell.leaveVoiceChannel()"
      >
        Leave
      </button>
    </section>

    <section class="voice-card" data-testid="voice-participants">
      <p>Participants</p>
      <span v-if="shell.voice.participants.length === 0">No participants</span>
      <span
        v-for="participant in shell.voice.participants"
        v-else
        :key="participant.userId"
      >
        {{ participant.userId }}
      </span>
    </section>

    <section class="voice-card voice-controls">
      <p data-testid="voice-local-state">{{ localStateLabel }}</p>
      <button
        type="button"
        data-testid="voice-toggle-mute"
        :disabled="!isHydrated || !shell.activeVoiceParticipant"
        @click="shell.toggleVoiceFlag('muted')"
      >
        Mute
      </button>
      <button
        type="button"
        data-testid="voice-toggle-deaf"
        :disabled="!isHydrated || !shell.activeVoiceParticipant"
        @click="shell.toggleVoiceFlag('deafened')"
      >
        Deaf
      </button>
      <button
        type="button"
        data-testid="voice-toggle-speaking"
        :disabled="!isHydrated || !shell.activeVoiceParticipant"
        @click="shell.toggleVoiceFlag('speaking')"
      >
        Speak
      </button>
      <button
        type="button"
        data-testid="voice-toggle-screen-share"
        :disabled="!isHydrated || !shell.activeVoiceParticipant"
        @click="shell.toggleVoiceFlag('screenSharing')"
      >
        Share screen
      </button>
    </section>

    <section class="voice-card voice-events" data-testid="voice-events">
      <p>Voice events</p>
      <article v-for="event in shell.voice.events" :key="event.id">
        <strong>{{ event.type }}</strong>
        <small>{{ event.detail }}</small>
      </article>
    </section>
  </aside>
</template>
