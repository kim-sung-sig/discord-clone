<script setup lang="ts">
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
</script>

<template>
  <section class="chat-viewport" data-testid="chat-viewport" aria-label="Messages">
    <header class="chat-header">
      <h2># {{ shell.activeChannel }}</h2>
      <p>Enterprise Discord clone bootstrap channel.</p>
    </header>
    <article
      v-for="message in shell.messages"
      :key="message.author + message.body"
      class="message"
    >
      <div class="avatar">{{ message.author.slice(0, 2).toUpperCase() }}</div>
      <div class="message-card">
        <strong>{{ message.author }}</strong>
        <p>{{ message.body }}</p>
      </div>
    </article>
  </section>
</template>
