<script setup lang="ts">
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
</script>

<template>
  <aside class="channel-sidebar" data-testid="channel-sidebar" aria-label="Channels">
    <h1>{{ shell.guildName }}</h1>
    <section
      v-for="group in shell.channelGroups"
      :key="group.name"
      class="channel-group"
    >
      <h2>{{ group.name }}</h2>
      <div
        v-for="channel in group.channels"
        :key="channel"
        class="channel-item"
        :aria-current="channel === shell.activeChannel ? 'page' : undefined"
      >
        <span>#</span>
        <span>{{ channel }}</span>
      </div>
    </section>
  </aside>
</template>
