<script setup lang="ts">
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
</script>

<template>
  <main class="app-shell" aria-label="Discord clone workspace">
    <ServerRail />
    <section class="workspace" data-testid="workspace">
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
      <ChannelSidebar />
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
  </main>
</template>
