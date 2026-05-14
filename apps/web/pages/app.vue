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

definePageMeta({
  path: '/'
})
</script>

<template>
  <main class="app-shell" aria-label="Discord clone workspace">
    <ServerRail />
    <section class="workspace" data-testid="workspace">
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
