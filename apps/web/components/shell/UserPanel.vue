<script setup lang="ts">
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
</script>

<template>
  <footer class="user-panel" data-testid="user-panel" aria-label="Current user">
    <strong>{{ shell.currentUser }}</strong>
    <span class="voice-state">{{ shell.voiceState }}</span>
  </footer>
</template>
