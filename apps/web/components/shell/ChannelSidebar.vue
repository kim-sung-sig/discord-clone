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
  <aside class="channel-sidebar" data-testid="channel-sidebar" aria-label="Channels">
    <h1 data-testid="guild-name">{{ shell.guild.name }}</h1>
    <section
      v-for="group in shell.channelGroups"
      :key="group.id"
      class="channel-group"
    >
      <h2>{{ group.name }}</h2>
      <p
        v-if="!isHydrated"
        id="channel-hydration-status"
        class="channel-loading"
        role="status"
      >
        Channels will be selectable after the page finishes loading.
      </p>
      <button
        v-for="channel in group.channels"
        :key="channel.id"
        class="channel-item"
        type="button"
        :data-testid="channel.id"
        :data-channel-id="channel.id"
        :disabled="!isHydrated"
        :aria-describedby="!isHydrated ? 'channel-hydration-status' : undefined"
        :aria-current="channel.id === shell.activeChannelId ? 'page' : undefined"
        @click="shell.selectChannel(channel.id)"
      >
        <span>{{ channel.type === 'GUILD_TEXT' ? '#' : 'Voice' }}</span>
        <span>{{ channel.name }}</span>
      </button>
    </section>
  </aside>
</template>
