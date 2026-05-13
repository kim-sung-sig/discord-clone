<script setup lang="ts">
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
</script>

<template>
  <aside class="gateway-status-panel" data-testid="gateway-status-panel" aria-label="Gateway status">
    <header class="gateway-status-header">
      <p>Gateway</p>
      <strong data-testid="gateway-status">{{ shell.gateway.status }}</strong>
    </header>

    <dl class="gateway-status-grid">
      <div>
        <dt>Session</dt>
        <dd>{{ shell.gateway.sessionId }}</dd>
      </div>
      <div>
        <dt>Sequence</dt>
        <dd data-testid="gateway-last-sequence">Last sequence {{ shell.gateway.lastSequence }}</dd>
      </div>
      <div>
        <dt>Heartbeat</dt>
        <dd data-testid="gateway-heartbeat-ack">{{ shell.gatewayHeartbeatAckLabel }}</dd>
      </div>
      <div>
        <dt>Resume</dt>
        <dd data-testid="gateway-resumed">{{ shell.gatewayResumeLabel }}</dd>
      </div>
    </dl>

    <section class="gateway-event-log" aria-label="Gateway events">
      <article
        v-for="event in shell.gateway.events"
        :key="event.sequence"
        class="gateway-event"
        :data-testid="`gateway-event-${event.sequence}`"
        :data-gateway-sequence="event.sequence"
      >
        <span>{{ event.sequence }}</span>
        <strong>{{ event.type }}</strong>
        <small>{{ event.label }}</small>
      </article>
    </section>
  </aside>
</template>
