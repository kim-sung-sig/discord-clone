<script setup lang="ts">
import ServerRail from '../components/shell/ServerRail.vue'
import ChannelSidebar from '../components/shell/ChannelSidebar.vue'
import ChatViewport from '../components/shell/ChatViewport.vue'
import MemberSidebar from '../components/shell/MemberSidebar.vue'
import RolePermissionPanel from '../components/shell/RolePermissionPanel.vue'
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
      <ChatViewport />
      <MemberSidebar />
      <RolePermissionPanel />
      <UserPanel />
    </section>
  </main>
</template>
