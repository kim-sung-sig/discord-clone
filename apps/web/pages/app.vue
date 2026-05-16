<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import ServerRail from '../components/shell/ServerRail.vue'
import ChannelSidebar from '../components/shell/ChannelSidebar.vue'
import ChatViewport from '../components/shell/ChatViewport.vue'
import ForumPanel from '../components/shell/ForumPanel.vue'
import ModerationPanel from '../components/shell/ModerationPanel.vue'
import VoicePanel from '../components/shell/VoicePanel.vue'
import ExperiencePanel from '../components/shell/ExperiencePanel.vue'
import InviteModal from '../components/invite/InviteModal.vue'
import DmSidebar from '../components/social/DmSidebar.vue'
import MemberSidebar from '../components/shell/MemberSidebar.vue'
import RolePermissionPanel from '../components/shell/RolePermissionPanel.vue'
import GatewayStatusPanel from '../components/shell/GatewayStatusPanel.vue'
import UserPanel from '../components/shell/UserPanel.vue'
import { useAuthStore } from '../stores/auth'
import { useShellStore } from '../stores/shell'

const auth = useAuthStore()
const shell = useShellStore()
type MobilePane = 'channels' | 'chat' | 'members' | 'voice'

const activeMobilePane = ref<MobilePane>('chat')
const mobileShellReady = ref(false)
const platformSurface = ref<'web-desktop' | 'pwa-mobile'>('web-desktop')
const mobilePanes: Array<{ id: MobilePane, label: string }> = [
  { id: 'channels', label: 'Channels' },
  { id: 'chat', label: 'Chat' },
  { id: 'members', label: 'Members' },
  { id: 'voice', label: 'Voice' }
]
const activeChannelLabel = computed(() => {
  const activeChannel = shell.activeChannel

  if (!activeChannel) {
    return 'No active channel'
  }

  return activeChannel.type === 'GUILD_TEXT'
    ? `# ${activeChannel.name}`
    : `Voice ${activeChannel.name}`
})

function updatePlatformSurface() {
  platformSurface.value = window.matchMedia('(max-width: 720px)').matches ? 'pwa-mobile' : 'web-desktop'
}

async function runRealBackendSmoke() {
  if (!auth.accessToken) {
    shell.apiError = 'Sign in before running the real backend smoke.'
    return
  }
  const stamp = Date.now()
  const guild = await shell.createBackendGuild(`Web Real Guild ${stamp}`, auth.accessToken)
  if (!guild) {
    return
  }
  const text = await shell.createBackendChannel(guild.id, `real-text-${stamp}`, 'GUILD_TEXT', auth.accessToken)
  if (!text) {
    return
  }
  const voice = await shell.createBackendChannel(guild.id, `real-voice-${stamp}`, 'GUILD_VOICE', auth.accessToken)
  if (!voice) {
    return
  }
  await shell.sendBackendMessage(text.id, 'real backend hello', auth.accessToken)
  await shell.joinBackendVoice(voice.id, auth.accessToken)
  await shell.startBackendStage(voice.id, 'Web Real Stage', auth.accessToken)
}

definePageMeta({
  path: '/'
})

onMounted(() => {
  auth.restoreSession()
  updatePlatformSurface()
  window.addEventListener('resize', updatePlatformSurface)
  mobileShellReady.value = true

  if ('serviceWorker' in navigator) {
    void navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => undefined)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updatePlatformSurface)
})
</script>

<template>
  <main class="app-shell" aria-label="Discord clone workspace" :data-platform-surface="platformSurface">
    <ServerRail />
    <header class="mobile-shell-bar" data-testid="mobile-shell-bar">
      <span>Discord Clone</span>
      <strong>{{ activeChannelLabel }}</strong>
    </header>
    <section
      class="workspace"
      :class="`mobile-pane-${activeMobilePane}`"
      data-testid="workspace"
      :data-mobile-active-pane="activeMobilePane"
    >
      <p v-if="shell.apiError" class="shell-api-error" data-testid="shell-api-error" role="alert">
        {{ shell.apiError }}
      </p>
      <section v-if="auth.accessToken" class="real-api-panel" data-testid="real-api-panel">
        <p>Real backend flow</p>
        <button
          type="button"
          data-testid="real-backend-smoke"
          :disabled="shell.apiBusy"
          @click="runRealBackendSmoke"
        >
          {{ shell.apiBusy ? 'Running...' : 'Run backend smoke' }}
        </button>
      </section>
      <ChannelSidebar @click.capture="activeMobilePane = 'chat'" />
      <DmSidebar />
      <ChatViewport />
      <ForumPanel />
      <ModerationPanel />
      <VoicePanel />
      <ExperiencePanel />
      <MemberSidebar />
      <RolePermissionPanel />
      <GatewayStatusPanel />
      <InviteModal />
      <UserPanel />
    </section>
    <nav class="mobile-shell-nav" data-testid="mobile-shell-nav" aria-label="Mobile shell navigation">
      <button
        v-for="pane in mobilePanes"
        :key="pane.id"
        type="button"
        role="tab"
        :data-testid="`mobile-nav-${pane.id}`"
        :aria-selected="activeMobilePane === pane.id"
        :disabled="!mobileShellReady"
        @click="activeMobilePane = pane.id"
      >
        {{ pane.label }}
      </button>
    </nav>
  </main>
</template>
