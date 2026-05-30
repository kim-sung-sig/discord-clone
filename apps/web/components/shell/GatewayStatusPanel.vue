<script setup lang="ts">
import { usePreferencesStore } from '../../stores/preferences'
import { useShellStore } from '../../stores/shell'

const shell = useShellStore()
const preferences = usePreferencesStore()
</script>

<template>
  <aside class="gateway-status-panel" data-testid="gateway-status-panel" aria-label="Gateway status">
    <header class="gateway-status-header">
      <p>{{ preferences.t('panel.gateway') }}</p>
      <strong data-testid="gateway-status">{{ shell.gateway.status }}</strong>
    </header>

    <dl class="gateway-status-grid">
      <div>
        <dt>{{ preferences.t('gateway.session') }}</dt>
        <dd>{{ shell.gateway.sessionId }}</dd>
      </div>
      <div>
        <dt>{{ preferences.t('gateway.sequence') }}</dt>
        <dd data-testid="gateway-last-sequence">{{ preferences.t('gateway.lastSequence') }} {{ shell.gateway.lastSequence }}</dd>
      </div>
      <div>
        <dt>{{ preferences.t('gateway.heartbeat') }}</dt>
        <dd data-testid="gateway-heartbeat-ack">{{ shell.gatewayHeartbeatAckLabel }}</dd>
      </div>
      <div>
        <dt>{{ preferences.t('gateway.resume') }}</dt>
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
