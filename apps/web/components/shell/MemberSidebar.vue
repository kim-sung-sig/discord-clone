<script setup lang="ts">
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
</script>

<template>
  <aside class="member-sidebar" data-testid="member-sidebar" aria-label="Members">
    <h2>Members</h2>
    <div
      v-for="member in shell.members"
      :key="member.name"
      class="member"
    >
      <span class="status-dot" aria-hidden="true" />
      <span>{{ member.name }}</span>
      <small>{{ member.status }}</small>
    </div>
  </aside>
</template>
