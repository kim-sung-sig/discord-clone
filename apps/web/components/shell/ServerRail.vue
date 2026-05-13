<script setup lang="ts">
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
</script>

<template>
  <aside class="server-rail" data-testid="server-rail" aria-label="Servers">
    <div class="server-pill">DC</div>
    <strong class="brand">{{ shell.guild.name }}</strong>
  </aside>
</template>
