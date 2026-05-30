<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { usePreferencesStore } from '../../stores/preferences'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const preferences = usePreferencesStore()
const isHydrated = ref(false)

const localStateLabel = computed(() => {
  const participant = shell.activeVoiceParticipant
  if (!participant) {
    return preferences.t('voice.disconnected')
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
      <p>{{ preferences.t('panel.voice') }}</p>
      <h2>{{ preferences.t('voice.sfuSkeleton') }}</h2>
      <small data-testid="voice-token-provider">{{ preferences.t('voice.provider') }} {{ shell.voice.tokenProvider }}</small>
    </header>

    <section class="voice-card">
      <p>{{ preferences.t('voice.guildChannel') }}</p>
      <button
        type="button"
        data-testid="voice-join-channel-war-room"
        @click="shell.joinVoiceChannel('channel-war-room')"
      >
        {{ preferences.t('voice.joinWarRoom') }}
      </button>
      <button
        type="button"
        data-testid="voice-leave"
        :disabled="!isHydrated || !shell.voice.activeChannelId"
        @click="shell.leaveVoiceChannel()"
      >
        {{ preferences.t('voice.leave') }}
      </button>
    </section>

    <section class="voice-card" data-testid="voice-participants">
      <p>{{ preferences.t('voice.participants') }}</p>
      <span v-if="shell.voice.participants.length === 0">{{ preferences.t('voice.noParticipants') }}</span>
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
        {{ preferences.t('voice.mute') }}
      </button>
      <button
        type="button"
        data-testid="voice-toggle-deaf"
        :disabled="!isHydrated || !shell.activeVoiceParticipant"
        @click="shell.toggleVoiceFlag('deafened')"
      >
        {{ preferences.t('voice.deaf') }}
      </button>
      <button
        type="button"
        data-testid="voice-toggle-speaking"
        :disabled="!isHydrated || !shell.activeVoiceParticipant"
        @click="shell.toggleVoiceFlag('speaking')"
      >
        {{ preferences.t('voice.speak') }}
      </button>
      <button
        type="button"
        data-testid="voice-toggle-screen-share"
        :disabled="!isHydrated || !shell.activeVoiceParticipant"
        @click="shell.toggleVoiceFlag('screenSharing')"
      >
        {{ preferences.t('voice.shareScreen') }}
      </button>
    </section>

    <section class="voice-card voice-events" data-testid="voice-events">
      <p>{{ preferences.t('voice.events') }}</p>
      <article v-for="event in shell.voice.events" :key="event.id">
        <strong>{{ event.type }}</strong>
        <small>{{ event.detail }}</small>
      </article>
    </section>
  </aside>
</template>
