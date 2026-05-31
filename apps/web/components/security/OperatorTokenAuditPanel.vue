<script setup lang="ts">
interface OperatorTokenAuditEntry {
  action: string
  tokenId: string
  actor: string
  at: string
  reason?: string
}

defineProps<{
  entries: OperatorTokenAuditEntry[]
  loading: boolean
  error: string
}>()

const actionLabel = (action: string): string => {
  switch (action) {
    case 'issued':
      return 'Issued'
    case 'revoked':
      return 'Revoked'
    default:
      return 'Updated'
  }
}
</script>

<template>
  <article class="security-dashboard-panel" data-testid="operator-token-audit">
    <header>
      <p>Operator access</p>
      <h2>Operator token audit</h2>
    </header>
    <p
      v-if="loading"
      class="security-dashboard-state"
      data-testid="operator-token-audit-loading"
    >
      Loading operator token audit
    </p>
    <p
      v-else-if="error"
      class="security-dashboard-state security-dashboard-error"
      data-testid="operator-token-audit-error"
    >
      {{ error }}
    </p>
    <p
      v-else-if="entries.length === 0"
      class="security-dashboard-state"
      data-testid="operator-token-audit-empty"
    >
      No operator token audit entries yet
    </p>
    <div v-else class="security-report-list">
      <article
        v-for="entry in entries"
        :key="`${entry.tokenId}-${entry.action}-${entry.at}`"
        class="security-report-card"
        :data-testid="`operator-token-audit-${entry.tokenId}-${entry.action}`"
      >
        <div>
          <strong>{{ actionLabel(entry.action) }}</strong>
          <span>{{ entry.at }}</span>
        </div>
        <p>{{ entry.reason ?? 'No operator reason recorded' }}</p>
        <small>{{ entry.actor }} &middot; token {{ entry.tokenId }}</small>
      </article>
    </div>
  </article>
</template>
