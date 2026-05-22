<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { CspTelemetryDashboard } from '../server/utils/csp-telemetry-dashboard'
import type { SecurityDashboardGuardHealth } from '../server/utils/security-dashboard-access'
import { useAuthStore } from '../stores/auth'

const dashboard = ref<CspTelemetryDashboard | null>(null)
const guardHealth = ref<SecurityDashboardGuardHealth | null>(null)
const loading = ref(true)
const error = ref('')
const guardHealthError = ref('')
const operatorTokenInput = ref('')
const operatorTokenSaved = ref(false)
const operatorTokenExpiresAt = ref('')
const alertAckReason = ref('')
const alertSnoozeMinutes = ref('')
const alertAckError = ref('')
const alertAckBusy = ref(false)
const auth = useAuthStore()
const operatorTokenStorageKey = 'dc_security_dashboard_operator_token'
const operatorTokenExpiryStorageKey = 'dc_security_dashboard_operator_token_expires_at'

const topDirectiveCount = computed(() => dashboard.value?.summary.topDirectives.length ?? 0)
const telemetryStorageHealth = computed(() => dashboard.value?.health?.storage)
const telemetryWriteFailures = computed(() => telemetryStorageHealth.value?.writeFailures ?? 0)
const rateLimitSubjectDiagnostics = computed(() => dashboard.value?.rateLimit?.subjectDiagnostics)
const rateLimitLifecycle = computed(() => dashboard.value?.rateLimit?.lifecycle)
const rateLimitRedisLifecycle = computed(() => rateLimitLifecycle.value?.redis)
const trendMaxTotal = computed(() => Math.max(1, ...(dashboard.value?.trend?.buckets.map((bucket) => bucket.total) ?? [0])))
type GuardHealthMethod = keyof SecurityDashboardGuardHealth['methods']

const guardHealthMethodLabels: Record<GuardHealthMethod, string> = {
  backend: 'Backend',
  jwt: 'JWT',
  operatorToken: 'Operator token',
  adminUserIds: 'Admin user IDs',
  adminRoles: 'Admin roles',
  adminScopes: 'Admin scopes'
}

const guardHealthMethods = computed(() => {
  const methods = guardHealth.value?.methods
  if (!methods) {
    return []
  }
  return Object.entries(guardHealthMethodLabels).map(([key, label]) => ({
    key,
    label,
    enabled: methods[key as GuardHealthMethod]
  }))
})

const guardHealthStatusLabel = computed(() => {
  switch (guardHealth.value?.status) {
    case 'ready':
      return 'Ready'
    case 'local-dev-open':
      return 'Local dev open'
    case 'fail-closed':
      return 'Fail closed'
    default:
      return 'Unknown'
  }
})

const guardHealthStatusClass = computed(() =>
  guardHealth.value ? `guard-status-${guardHealth.value.status}` : 'guard-status-unknown'
)

const backendAuthProbeLabel = computed(() => {
  const check = guardHealth.value?.backendCheck
  if (!check?.configured) {
    return 'Not configured'
  }
  return check.reachable ? 'Reachable' : 'Unreachable'
})

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

const subjectDiagnosticsTrustLabel = computed(() => {
  const diagnostics = rateLimitSubjectDiagnostics.value
  if (!diagnostics?.trustedProxyConfigured) {
    return 'Not configured'
  }
  return diagnostics.trustedProxyMatched ? 'Matched' : 'Not matched'
})

const alertAckStatus = computed(() => dashboard.value?.alertAcknowledgement?.status ?? 'unacknowledged')
const alertAckStatusLabel = computed(() => {
  switch (alertAckStatus.value) {
    case 'snoozed':
      return 'Snoozed'
    case 'acknowledged':
      return 'Acknowledged'
    default:
      return 'Unacknowledged'
  }
})

const incidentEventLabel = (eventType: string): string => {
  switch (eventType) {
    case 'snoozed':
      return 'Snoozed'
    case 'acknowledged':
      return 'Acknowledged'
    case 'assigned':
      return 'Assigned'
    case 'status_changed':
      return 'Status changed'
    default:
      return 'Updated'
  }
}

onMounted(async () => {
  const storedToken = window.sessionStorage.getItem(operatorTokenStorageKey)?.trim() ?? ''
  const storedExpiry = window.sessionStorage.getItem(operatorTokenExpiryStorageKey)?.trim() ?? ''
  operatorTokenSaved.value = storedToken.length > 0
  operatorTokenExpiresAt.value = storedToken ? storedExpiry : ''
  await loadDashboard()
})

const loadDashboard = async () => {
  loading.value = true
  error.value = ''
  guardHealthError.value = ''
  try {
    const response = await fetch('/api/security/csp-telemetry?limit=25', {
      headers: dashboardRequestHeaders()
    })
    if (!response.ok) {
      throw new Error('dashboard read failed')
    }
    dashboard.value = await response.json() as CspTelemetryDashboard
    loading.value = false
    await loadGuardHealth()
  } catch {
    error.value = 'Unable to load browser security telemetry.'
    loading.value = false
  } finally {
    loading.value = false
  }
}

const loadGuardHealth = async () => {
  guardHealth.value = null
  guardHealthError.value = ''
  try {
    const response = await fetch('/api/security/dashboard-guard-health', {
      headers: dashboardRequestHeaders()
    })
    if (!response.ok) {
      guardHealthError.value = 'Dashboard guard health is unavailable.'
      return
    }
    const payload = await response.json()
    guardHealth.value = isSecurityDashboardGuardHealth(payload) ? payload : null
  } catch {
    guardHealthError.value = 'Dashboard guard health is unavailable.'
  }
}

const saveOperatorTokenAndRetry = async () => {
  const bootstrapToken = operatorTokenInput.value.trim()
  operatorTokenInput.value = bootstrapToken
  if (bootstrapToken) {
    const response = await fetch('/api/security/operator-token/exchange', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        'x-operator-token': bootstrapToken
      }
    })
    if (!response.ok) {
      error.value = 'Unable to issue a short-lived operator token.'
      return
    }
    const payload = await response.json()
    const issuedToken = typeof payload.token === 'string' ? payload.token.trim() : ''
    const expiresAt = typeof payload.expiresAt === 'string' ? payload.expiresAt.trim() : ''
    if (!issuedToken || !expiresAt) {
      error.value = 'Unable to issue a short-lived operator token.'
      return
    }
    window.sessionStorage.setItem(operatorTokenStorageKey, issuedToken)
    window.sessionStorage.setItem(operatorTokenExpiryStorageKey, expiresAt)
    operatorTokenInput.value = ''
    operatorTokenSaved.value = true
    operatorTokenExpiresAt.value = expiresAt
  } else {
    window.sessionStorage.removeItem(operatorTokenStorageKey)
    window.sessionStorage.removeItem(operatorTokenExpiryStorageKey)
    operatorTokenSaved.value = false
    operatorTokenExpiresAt.value = ''
  }
  await loadDashboard()
}

const clearOperatorToken = async () => {
  const storedToken = window.sessionStorage.getItem(operatorTokenStorageKey)?.trim()
  if (storedToken) {
    await fetch('/api/security/operator-token/revoke', {
      method: 'POST',
      headers: dashboardRequestHeaders()
    }).catch(() => undefined)
  }
  operatorTokenInput.value = ''
  operatorTokenSaved.value = false
  operatorTokenExpiresAt.value = ''
  window.sessionStorage.removeItem(operatorTokenStorageKey)
  window.sessionStorage.removeItem(operatorTokenExpiryStorageKey)
  await loadDashboard()
}

const acknowledgeAlert = async () => {
  if (!dashboard.value?.alert?.active || !dashboard.value.alert.fingerprint) {
    return
  }
  alertAckBusy.value = true
  alertAckError.value = ''
  try {
    const reason = String(alertAckReason.value ?? '').trim()
    const snoozeText = String(alertSnoozeMinutes.value ?? '').trim()
    alertAckReason.value = reason
    const response = await fetch('/api/security/csp-alert-ack', {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        ...dashboardRequestHeaders()
      },
      body: JSON.stringify({
        fingerprint: dashboard.value.alert.fingerprint,
        reason,
        ...(snoozeText
          ? { snoozeMinutes: Number(snoozeText) }
          : {})
      })
    })
    if (!response.ok) {
      throw new Error('acknowledgement failed')
    }
    alertAckReason.value = ''
    alertSnoozeMinutes.value = ''
    await loadDashboard()
  } catch {
    alertAckError.value = 'Unable to acknowledge the current CSP alert.'
  } finally {
    alertAckBusy.value = false
  }
}

const dashboardRequestHeaders = (): Record<string, string> => {
  const headers: Record<string, string> = {}
  if (auth.accessToken) {
    headers.Authorization = `Bearer ${auth.accessToken}`
  }
  const operatorToken = window.sessionStorage.getItem(operatorTokenStorageKey)?.trim()
  if (operatorToken) {
    headers['x-operator-token'] = operatorToken
  }
  return headers
}

const trendBarHeight = (total: number): string => `${Math.max(8, Math.round((total / trendMaxTotal.value) * 100))}%`

const isSecurityDashboardGuardHealth = (value: unknown): value is SecurityDashboardGuardHealth => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false
  }
  const candidate = value as Partial<SecurityDashboardGuardHealth>
  const methods = candidate.methods
  return (
    typeof candidate.configured === 'boolean'
    && typeof candidate.requireConfiguredGuard === 'boolean'
    && (candidate.status === 'ready' || candidate.status === 'local-dev-open' || candidate.status === 'fail-closed')
    && Boolean(methods)
    && typeof methods?.backend === 'boolean'
    && typeof methods?.jwt === 'boolean'
    && typeof methods?.operatorToken === 'boolean'
    && typeof methods?.adminUserIds === 'boolean'
    && typeof methods?.adminRoles === 'boolean'
    && typeof methods?.adminScopes === 'boolean'
    && (
      candidate.backendCheck === undefined
      || (
        typeof candidate.backendCheck === 'object'
        && candidate.backendCheck !== null
        && !Array.isArray(candidate.backendCheck)
        && typeof candidate.backendCheck.configured === 'boolean'
        && typeof candidate.backendCheck.reachable === 'boolean'
        && typeof candidate.backendCheck.checkedAt === 'string'
        && (
          candidate.backendCheck.statusCode === undefined
          || typeof candidate.backendCheck.statusCode === 'number'
        )
      )
    )
    && Array.isArray(candidate.warnings)
    && candidate.warnings.every((warning) => typeof warning === 'string')
  )
}
</script>

<template>
  <main class="security-dashboard" data-testid="security-dashboard" aria-label="Browser security dashboard">
    <header class="security-dashboard-header">
      <p>Security operations</p>
      <h1>Browser security</h1>
    </header>

    <form
      class="security-operator-token"
      data-testid="operator-token-form"
      aria-label="Operator token"
      @submit.prevent="saveOperatorTokenAndRetry"
    >
      <label for="operator-token-input">Operator token</label>
      <div>
        <input
          id="operator-token-input"
          v-model="operatorTokenInput"
          data-testid="operator-token-input"
          type="password"
          autocomplete="off"
        >
        <button type="submit">Issue</button>
        <button v-if="operatorTokenSaved" type="button" @click="clearOperatorToken">Clear</button>
      </div>
      <small v-if="operatorTokenExpiresAt" data-testid="operator-token-expiry">
        Expires {{ operatorTokenExpiresAt }}
      </small>
    </form>

    <p v-if="loading" class="security-dashboard-state" data-testid="csp-loading-state">Loading telemetry</p>
    <p v-else-if="error" class="security-dashboard-state security-dashboard-error" data-testid="csp-error-state">
      {{ error }}
    </p>

    <template v-else-if="dashboard">
      <section
        v-if="dashboard.alert?.active"
        class="security-alert-banner"
        data-testid="csp-alert-banner"
        role="alert"
        aria-label="Active CSP alert"
      >
        <div>
          <p>CSP alert active</p>
          <strong>Threshold exceeded</strong>
          <span data-testid="csp-alert-ack-status">{{ alertAckStatusLabel }}</span>
          <small v-if="dashboard.alertAcknowledgement?.snoozeUntil">
            Snoozed until {{ dashboard.alertAcknowledgement.snoozeUntil }}
          </small>
          <small v-else-if="dashboard.alertAcknowledgement?.acknowledgedAt">
            {{ dashboard.alertAcknowledgement.reason }} · {{ dashboard.alertAcknowledgement.acknowledgedAt }}
          </small>
        </div>
        <ul>
          <li v-for="reason in dashboard.alert.reasons" :key="reason">{{ reason }}</li>
        </ul>
        <form
          class="csp-alert-ack-form"
          data-testid="csp-alert-ack-form"
          aria-label="Acknowledge CSP alert"
          novalidate
          @submit.prevent="acknowledgeAlert"
        >
          <label for="csp-alert-ack-reason">Ack reason</label>
          <select
            id="csp-alert-ack-reason"
            v-model="alertAckReason"
            data-testid="csp-alert-ack-reason"
            :disabled="alertAckBusy"
            required
          >
            <option value="">Select reason</option>
            <option value="Known deployment">Known deployment</option>
            <option value="Expected CSP test">Expected CSP test</option>
            <option value="Investigating">Investigating</option>
            <option value="False positive">False positive</option>
          </select>
          <label for="csp-alert-snooze-minutes">Snooze minutes</label>
          <input
            id="csp-alert-snooze-minutes"
            v-model="alertSnoozeMinutes"
            data-testid="csp-alert-snooze-minutes"
            type="number"
            min="1"
            max="1440"
            inputmode="numeric"
            :disabled="alertAckBusy"
          >
          <button type="submit" data-testid="csp-alert-ack-submit" :disabled="alertAckBusy">Acknowledge</button>
          <p v-if="alertAckError" data-testid="csp-alert-ack-error">{{ alertAckError }}</p>
        </form>
      </section>

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

      <section class="security-dashboard-grid">
        <article
          v-if="guardHealth || guardHealthError"
          class="security-dashboard-panel guard-health-panel"
          data-testid="dashboard-guard-health"
        >
          <header>
            <p>Access guard</p>
            <h2>Dashboard guard health</h2>
          </header>
          <p
            v-if="guardHealthError"
            class="security-dashboard-state security-dashboard-error"
            data-testid="dashboard-guard-health-error"
          >
            {{ guardHealthError }}
          </p>
          <template v-else-if="guardHealth">
            <div class="guard-health-summary">
              <strong :class="guardHealthStatusClass">{{ guardHealthStatusLabel }}</strong>
              <span>{{ guardHealth.configured ? 'Configured' : 'Not configured' }}</span>
              <span>{{ guardHealth.requireConfiguredGuard ? 'Guard required' : 'Guard optional' }}</span>
            </div>
            <ul class="guard-method-list" aria-label="Dashboard guard methods">
              <li
                v-for="method in guardHealthMethods"
                :key="method.key"
                :class="method.enabled ? 'guard-method-enabled' : 'guard-method-disabled'"
              >
                <span>{{ method.label }}</span>
                <strong>{{ method.enabled ? 'On' : 'Off' }}</strong>
              </li>
            </ul>
            <div
              v-if="guardHealth.backendCheck?.configured"
              class="backend-auth-probe"
              data-testid="dashboard-backend-auth-probe"
            >
              <span>Backend auth probe</span>
              <strong>{{ backendAuthProbeLabel }}</strong>
              <small
                v-if="guardHealth.backendCheck.statusCode"
                data-testid="dashboard-backend-auth-probe-status"
              >
                HTTP {{ guardHealth.backendCheck.statusCode }}
              </small>
              <small>{{ guardHealth.backendCheck.checkedAt }}</small>
            </div>
            <ul v-if="guardHealth.warnings.length > 0" class="guard-warning-list">
              <li v-for="warning in guardHealth.warnings" :key="warning">{{ warning }}</li>
            </ul>
          </template>
        </article>

        <article
          v-if="rateLimitSubjectDiagnostics"
          class="security-dashboard-panel"
          data-testid="csp-rate-limit-subject-diagnostics"
        >
          <header>
            <p>CSP rate limit</p>
            <h2>Subject diagnostics</h2>
          </header>
          <ul class="subject-diagnostics-list" aria-label="CSP rate-limit subject diagnostics">
            <li>
              <span>Source</span>
              <strong data-testid="csp-subject-source">{{ rateLimitSubjectDiagnostics.source }}</strong>
            </li>
            <li>
              <span>Trusted proxy</span>
              <strong data-testid="csp-subject-trusted-proxy">{{ subjectDiagnosticsTrustLabel }}</strong>
            </li>
            <li>
              <span>Trusted rules</span>
              <strong data-testid="csp-subject-rule-count">{{ rateLimitSubjectDiagnostics.trustedProxyRuleCount }}</strong>
            </li>
            <li>
              <span>Forwarded header</span>
              <strong>{{ rateLimitSubjectDiagnostics.forwardedForPresent ? 'Present' : 'Absent' }}</strong>
            </li>
            <li>
              <span>Real-IP header</span>
              <strong>{{ rateLimitSubjectDiagnostics.realIpPresent ? 'Present' : 'Absent' }}</strong>
            </li>
            <li>
              <span>Subject hash</span>
              <strong data-testid="csp-subject-hash-prefix">{{ rateLimitSubjectDiagnostics.subjectHashPrefix }}</strong>
            </li>
          </ul>
        </article>

        <article
          v-if="rateLimitLifecycle"
          class="security-dashboard-panel"
          data-testid="csp-rate-limit-lifecycle"
        >
          <header>
            <p>CSP rate limit</p>
            <h2>Redis lifecycle</h2>
          </header>
          <ul class="subject-diagnostics-list" aria-label="CSP rate-limit lifecycle metrics">
            <li>
              <span>Backend</span>
              <strong data-testid="csp-rate-limit-backend">{{ rateLimitLifecycle.backend }}</strong>
            </li>
            <li>
              <span>Fail closed</span>
              <strong data-testid="csp-rate-limit-fail-closed">{{ rateLimitLifecycle.failClosedDecisions }}</strong>
            </li>
            <li v-if="rateLimitRedisLifecycle">
              <span>Connect attempts</span>
              <strong>{{ rateLimitRedisLifecycle.connectAttempts }}</strong>
            </li>
            <li v-if="rateLimitRedisLifecycle">
              <span>Connect failures</span>
              <strong data-testid="csp-rate-limit-connect-failures">
                {{ rateLimitRedisLifecycle.connectFailures }}
              </strong>
            </li>
            <li v-if="rateLimitRedisLifecycle">
              <span>Reconnects</span>
              <strong>{{ rateLimitRedisLifecycle.reconnectEvents }}</strong>
            </li>
            <li v-if="rateLimitRedisLifecycle">
              <span>Error events</span>
              <strong data-testid="csp-rate-limit-error-events">{{ rateLimitRedisLifecycle.errorEvents }}</strong>
            </li>
            <li v-if="rateLimitRedisLifecycle">
              <span>Close calls</span>
              <strong>{{ rateLimitRedisLifecycle.closeCalls }}</strong>
            </li>
            <li v-if="rateLimitRedisLifecycle?.lastErrorAt">
              <span>Last error</span>
              <strong>{{ rateLimitRedisLifecycle.lastErrorAt }}</strong>
            </li>
          </ul>
        </article>

        <article class="security-dashboard-panel">
          <header>
            <p>Top directives</p>
            <h2>CSP violation mix</h2>
          </header>
          <p
            v-if="dashboard.summary.topDirectives.length === 0"
            class="security-dashboard-state"
            data-testid="csp-empty-state"
          >
            No CSP reports recorded
          </p>
          <ol v-else class="directive-list">
            <li
              v-for="directive in dashboard.summary.topDirectives"
              :key="directive.directive"
              :data-testid="`csp-directive-${directive.directive}`"
            >
              <span>{{ directive.directive }}</span>
              <strong>{{ directive.count }}</strong>
            </li>
          </ol>
        </article>

        <article
          v-if="dashboard.trend"
          class="security-dashboard-panel"
          data-testid="csp-trend-chart"
        >
          <header>
            <p>Last <span data-testid="csp-trend-window">{{ dashboard.trend.windowHours }}h</span></p>
            <h2>CSP trend</h2>
          </header>
          <div class="csp-trend-bars" aria-label="CSP reports by hour">
            <div
              v-for="bucket in dashboard.trend.buckets"
              :key="bucket.bucketStart"
              class="csp-trend-bucket"
              :data-testid="`csp-trend-bucket-${bucket.bucketStart}`"
            >
              <span>{{ bucket.total }}</span>
              <div>
                <i :style="{ height: trendBarHeight(bucket.total) }" />
              </div>
              <small>{{ bucket.bucketStart.slice(11, 16) }}</small>
            </div>
          </div>
        </article>

        <article class="security-dashboard-panel">
          <header>
            <p>Recent reports</p>
            <h2>Sanitized telemetry</h2>
          </header>
          <p
            v-if="dashboard.recent.length === 0"
            class="security-dashboard-state"
            data-testid="csp-recent-empty-state"
          >
            No recent browser reports
          </p>
          <div v-else class="security-report-list">
            <article
              v-for="report in dashboard.recent"
              :key="report.requestId"
              class="security-report-card"
              :data-testid="`csp-report-${report.requestId}`"
            >
              <div>
                <strong>{{ report.effectiveDirective }}</strong>
                <span>{{ report.disposition }}</span>
              </div>
              <p>{{ report.blockedUriOrigin }}</p>
              <small>{{ report.documentUriOrigin }} · {{ report.receivedAt }}</small>
            </article>
          </div>
        </article>

        <article class="security-dashboard-panel" data-testid="csp-alert-history">
          <header>
            <p>Alert history</p>
            <h2>CSP state transitions</h2>
          </header>
          <p
            v-if="(dashboard.alertHistory?.length ?? 0) === 0"
            class="security-dashboard-state"
            data-testid="csp-alert-history-empty-state"
          >
            No alert transitions recorded
          </p>
          <div v-else class="security-report-list">
            <article
              v-for="transition in dashboard.alertHistory"
              :key="transition.observedAt"
              class="security-report-card"
              :data-testid="`csp-alert-history-${transition.observedAt}`"
            >
              <div>
                <strong>{{ transition.active ? 'Active' : 'Cleared' }}</strong>
                <span>{{ transition.observedAt }}</span>
              </div>
              <p v-if="transition.reasons.length > 0">{{ transition.reasons.join('; ') }}</p>
              <p v-else>No active threshold reasons</p>
            </article>
          </div>
        </article>

        <article class="security-dashboard-panel" data-testid="csp-alert-incident-history">
          <header>
            <p>Incident lifecycle</p>
            <h2>CSP operator history</h2>
          </header>
          <p
            v-if="(dashboard.alertIncidentHistory?.length ?? 0) === 0"
            class="security-dashboard-state"
            data-testid="csp-alert-incident-history-empty-state"
          >
            No operator incident events recorded
          </p>
          <div v-else class="security-report-list">
            <article
              v-for="event in dashboard.alertIncidentHistory"
              :key="`${event.occurredAt}-${event.eventType}-${event.actor}`"
              class="security-report-card"
              :data-testid="`csp-alert-incident-${event.occurredAt}`"
            >
              <div>
                <strong>{{ incidentEventLabel(event.eventType) }}</strong>
                <span>{{ event.occurredAt }}</span>
              </div>
              <p>{{ event.reason ?? 'No operator reason recorded' }}</p>
              <small>
                {{ event.actor }}
                <template v-if="event.assignedTo"> Â· assigned to {{ event.assignedTo }}</template>
                <template v-if="event.snoozeUntil"> Â· snoozed until {{ event.snoozeUntil }}</template>
              </small>
            </article>
          </div>
        </article>
      </section>
    </template>
  </main>
</template>
