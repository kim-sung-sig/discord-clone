<script setup lang="ts">
import { computed } from 'vue'
import type { CspTelemetryDashboard } from '../../server/utils/csp-telemetry-dashboard'

const props = defineProps<{
  dashboard: CspTelemetryDashboard
}>()

const topDirectiveCount = computed(() => props.dashboard.summary.topDirectives.length)
const telemetryStorageHealth = computed(() => props.dashboard.health?.storage)
const telemetryWriteFailures = computed(() => telemetryStorageHealth.value?.writeFailures ?? 0)

const telemetryStorageLabel = computed(() => {
  const health = telemetryStorageHealth.value
  if (!health) {
    return 'Unknown'
  }
  const backend = health.backend === 'postgres' ? 'Postgres' : health.backend === 'memory' ? 'Memory' : 'Unknown'
  if (health.ok) {
    return `${backend} ready`
  }
  return `${backend} degraded`
})

const telemetryStorageClass = computed(() =>
  telemetryStorageHealth.value?.ok === false ? 'storage-health-degraded' : 'storage-health-ready'
)
</script>

<template>
  <section class="security-summary-strip" aria-label="CSP telemetry summary">
    <article class="security-summary-card">
      <span>Total CSP reports</span>
      <strong data-testid="csp-total">{{ dashboard.summary.total }}</strong>
    </article>
    <article class="security-summary-card">
      <span>Rate-limited reports</span>
      <strong data-testid="csp-rate-limit-limited">{{ dashboard.rateLimit?.limitedTotal ?? 0 }}</strong>
    </article>
    <article class="security-summary-card">
      <span>Directive groups</span>
      <strong data-testid="csp-directive-count">{{ topDirectiveCount }}</strong>
    </article>
    <article class="security-summary-card">
      <span>Discarded by retention</span>
      <strong data-testid="csp-retention-discarded">{{ dashboard.retention?.discardedTotal ?? 0 }}</strong>
      <dl class="retention-breakdown" aria-label="CSP retention discard breakdown">
        <div>
          <dt>Age</dt>
          <dd data-testid="csp-retention-discarded-by-age">{{ dashboard.retention?.discardedByAge ?? 0 }}</dd>
        </div>
        <div>
          <dt>Max entries</dt>
          <dd data-testid="csp-retention-discarded-by-max-entries">
            {{ dashboard.retention?.discardedByMaxEntries ?? 0 }}
          </dd>
        </div>
      </dl>
    </article>
    <article class="security-summary-card">
      <span>Telemetry storage</span>
      <strong
        :class="telemetryStorageClass"
        data-testid="csp-telemetry-storage-health"
      >
        {{ telemetryStorageLabel }}
      </strong>
    </article>
    <article class="security-summary-card">
      <span>Write failures</span>
      <strong data-testid="csp-telemetry-write-failures">{{ telemetryWriteFailures }}</strong>
    </article>
  </section>
</template>
