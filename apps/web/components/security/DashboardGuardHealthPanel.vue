<script setup lang="ts">
import { computed } from 'vue'
import type { SecurityDashboardGuardHealth } from '../../server/utils/security-dashboard-access'

const props = defineProps<{
  guardHealth: SecurityDashboardGuardHealth | null
  error: string
}>()

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
  const methods = props.guardHealth?.methods
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
  switch (props.guardHealth?.status) {
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
  props.guardHealth ? `guard-status-${props.guardHealth.status}` : 'guard-status-unknown'
)

const backendAuthProbeLabel = computed(() => {
  const check = props.guardHealth?.backendCheck
  if (!check?.configured) {
    return 'Not configured'
  }
  return check.reachable ? 'Reachable' : 'Unreachable'
})
</script>

<template>
  <article
    v-if="guardHealth || error"
    class="security-dashboard-panel guard-health-panel"
    data-testid="dashboard-guard-health"
  >
    <header>
      <p>Access guard</p>
      <h2>Dashboard guard health</h2>
    </header>
    <p
      v-if="error"
      class="security-dashboard-state security-dashboard-error"
      data-testid="dashboard-guard-health-error"
    >
      {{ error }}
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
</template>
