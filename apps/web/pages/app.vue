<script setup lang="ts">
import ServerRail from '../components/shell/ServerRail.vue'
import ChannelSidebar from '../components/shell/ChannelSidebar.vue'
import ChatViewport from '../components/shell/ChatViewport.vue'
import MemberSidebar from '../components/shell/MemberSidebar.vue'
import UserPanel from '../components/shell/UserPanel.vue'

definePageMeta({
  path: '/'
})
</script>

<template>
  <main class="app-shell" aria-label="Discord clone workspace">
    <ServerRail />
    <section class="workspace">
      <ChannelSidebar />
      <ChatViewport />
      <MemberSidebar />
      <UserPanel />
    </section>
  </main>
</template>
