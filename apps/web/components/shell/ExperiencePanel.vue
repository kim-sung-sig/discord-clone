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
  <aside class="experience-panel" data-testid="experience-panel" aria-label="Experience">
    <header class="experience-panel-header">
      <p>Experience</p>
      <h2>Stage, Soundboard, Premium</h2>
    </header>

    <section class="experience-card">
      <p>Stage channel</p>
      <strong data-testid="stage-topic">
        {{ shell.experience.stageSession?.topic ?? 'No active stage' }}
      </strong>
      <button
        type="button"
        data-testid="stage-start"
        :disabled="!isHydrated"
        @click="shell.startStageSession()"
      >
        Start stage
      </button>
      <button
        type="button"
        data-testid="stage-request-speak"
        :disabled="!isHydrated || !shell.experience.stageSession"
        @click="shell.requestToSpeak()"
      >
        Request to speak
      </button>
    </section>

    <section class="experience-card stage-roster">
      <p>Pending</p>
      <span data-testid="stage-pending">
        {{ shell.experience.stageSession?.pendingRequests.join(', ') || 'No pending requests' }}
      </span>
      <button
        v-for="userId in shell.experience.stageSession?.pendingRequests ?? []"
        :key="`approve-${userId}`"
        type="button"
        :data-testid="`stage-approve-${userId}`"
        :disabled="!isHydrated"
        @click="shell.approveStageSpeaker(userId)"
      >
        Approve {{ userId }}
      </button>
    </section>

    <section class="experience-card stage-roster">
      <p>Speakers</p>
      <span data-testid="stage-speakers">
        {{ shell.experience.stageSession?.speakers.join(', ') || 'No speakers' }}
      </span>
      <button
        v-for="userId in shell.experience.stageSession?.speakers ?? []"
        :key="`audience-${userId}`"
        type="button"
        :data-testid="`stage-move-audience-${userId}`"
        :disabled="!isHydrated"
        @click="shell.moveStageAudience(userId)"
      >
        Move {{ userId }} to audience
      </button>
      <small data-testid="stage-audience">
        Audience: {{ shell.experience.stageSession?.audience.join(', ') || 'none' }}
      </small>
    </section>

    <section class="experience-card">
      <p>Soundboard</p>
      <button
        type="button"
        data-testid="soundboard-create-applause"
        :disabled="!isHydrated"
        @click="shell.createSoundboardSound()"
      >
        Register Applause
      </button>
      <button
        type="button"
        data-testid="soundboard-play-applause"
        :disabled="!isHydrated || shell.experience.soundboardSounds.length === 0"
        @click="shell.playSoundboardSound()"
      >
        Play Applause
      </button>
      <span data-testid="soundboard-sounds">
        {{ shell.experience.soundboardSounds.map((sound) => sound.label).join(', ') || 'No sounds' }}
      </span>
      <strong data-testid="soundboard-last-event">
        {{
          shell.experience.lastSoundboardEvent
            ? `${shell.experience.lastSoundboardEvent.userId} played ${shell.experience.lastSoundboardEvent.soundLabel} in ${shell.experience.lastSoundboardEvent.channelName}`
            : 'No sound played'
        }}
      </strong>
    </section>

    <section class="experience-card">
      <p>Premium</p>
      <strong data-testid="premium-gate">{{ shell.experience.premiumGate }}</strong>
      <button
        type="button"
        data-testid="premium-check-hd-stream"
        :disabled="!isHydrated"
        @click="shell.checkPremiumFeature()"
      >
        Check HD_STREAM
      </button>
      <button
        type="button"
        data-testid="premium-grant-hd-stream"
        :disabled="!isHydrated"
        @click="shell.grantPremiumEntitlement()"
      >
        Grant HD_STREAM
      </button>
      <span data-testid="premium-catalog">
        {{ shell.experience.catalog.map((item) => item.label).join(', ') }}
      </span>
      <small data-testid="premium-quests">
        {{ shell.experience.quests.map((quest) => quest.title).join(', ') }}
      </small>
    </section>
  </aside>
</template>
