import type {
  CspTelemetryRetentionMetrics,
  CspTelemetryStorageHealth,
  CspTelemetryStore,
  StoredCspTelemetry
} from './csp-telemetry-store'
import {
  evaluateCspTelemetryAlert,
  type CspAlertThresholdOptions,
  type CspTelemetryAlert
} from './csp-alert-threshold'
import type {
  CspRateLimitSubjectDistribution,
  CspRateLimitTelemetryStore
} from './csp-rate-limit-telemetry-store'
import type { CspRateLimitSubjectDiagnostics } from './csp-rate-limit-subject'
import type { CspReportRateLimiter, CspReportRateLimiterLifecycleMetrics } from './csp-report-rate-limiter'
import type {
  CspAlertTransition,
  CspAlertTransitionStore
} from './csp-alert-transition-store'
import {
  cspAlertFingerprint,
  type CspAlertAcknowledgement,
  type CspAlertAcknowledgementStore,
  unacknowledgedCspAlert
} from './csp-alert-acknowledgement-store'
import type {
  CspAlertIncidentEvent,
  CspAlertIncidentStore
} from './csp-alert-incident-store'

export interface CspTelemetryDashboardOptions {
  recentLimit?: number
  trendHours?: number
  trendLimit?: number
  rateLimitTelemetryStore?: CspRateLimitTelemetryStore
  rateLimitSubjectDiagnostics?: CspRateLimitSubjectDiagnostics
  rateLimiter?: CspReportRateLimiter
  alertTransitionStore?: CspAlertTransitionStore
  alertAcknowledgementStore?: CspAlertAcknowledgementStore
  alertIncidentStore?: CspAlertIncidentStore
  alertHistoryLimit?: number
  alertIncidentHistoryLimit?: number
  alertThresholds?: CspAlertThresholdOptions
  now?: () => Date
}

export interface CspTelemetryDashboardDirective {
  directive: string
  count: number
}

export interface CspTelemetryDashboardReport {
  requestId: string
  receivedAt: string
  effectiveDirective: string
  violatedDirective: string
  blockedUriOrigin: string
  documentUriOrigin: string
  disposition: string
}

export interface CspTelemetryDashboardTrendBucket {
  bucketStart: string
  total: number
}

export interface CspTelemetryDashboardTrend {
  windowHours: number
  buckets: CspTelemetryDashboardTrendBucket[]
}

export interface CspTelemetryDashboard {
  summary: {
    total: number
    byEffectiveDirective: Record<string, number>
    topDirectives: CspTelemetryDashboardDirective[]
  }
  rateLimit: {
    limitedTotal: number
    subjectDistribution: CspRateLimitSubjectDistribution
    subjectDiagnostics?: CspRateLimitSubjectDiagnostics
    lifecycle?: CspReportRateLimiterLifecycleMetrics
  }
  retention: CspTelemetryRetentionMetrics
  alert: CspTelemetryAlert
  alertAcknowledgement: CspAlertAcknowledgement
  alertHistory: CspAlertTransition[]
  alertIncidentHistory: CspAlertIncidentEvent[]
  trend: CspTelemetryDashboardTrend
  health: {
    storage: CspTelemetryStorageHealth
  }
  recent: CspTelemetryDashboardReport[]
}

export interface CspTelemetryOperatorAuthorizationInput {
  configuredToken?: string
  providedToken?: string
}

export const buildCspTelemetryDashboard = async (
  store: CspTelemetryStore,
  options: CspTelemetryDashboardOptions = {}
): Promise<CspTelemetryDashboard> => {
  const limit = Math.min(Math.max(Math.trunc(options.recentLimit ?? 25), 0), 100)
  const trendHours = Math.min(Math.max(Math.trunc(options.trendHours ?? 6), 1), 24)
  const trendLimit = Math.min(Math.max(Math.trunc(options.trendLimit ?? 500), 0), 1_000)
  const summary = await store.summary()
  const retention = await store.retentionMetrics()
  const recent = await store.recent(limit)
  const trendEntries = await store.recent(trendLimit)
  const rateLimitSummary = await options.rateLimitTelemetryStore?.summary()
  const rateLimitLifecycle = options.rateLimiter?.lifecycleMetrics?.()
  const storageHealth = await cspTelemetryStorageHealth(store)
  const observedAt = options.now?.() ?? new Date()
  const evaluatedAlert = evaluateCspTelemetryAlert(summary, options.alertThresholds)
  const alert: CspTelemetryAlert = {
    ...evaluatedAlert,
    fingerprint: cspAlertFingerprint(evaluatedAlert)
  }
  if (options.alertTransitionStore) {
    await options.alertTransitionStore.recordIfChanged(alert, observedAt)
  }
  const alertHistory = await options.alertTransitionStore?.recent(options.alertHistoryLimit ?? 10)
  const alertAcknowledgement = alert.active && alert.fingerprint
    ? await options.alertAcknowledgementStore?.current(alert.fingerprint, observedAt) ?? unacknowledgedCspAlert(alert.fingerprint)
    : unacknowledgedCspAlert('')
  const alertIncidentHistory = await options.alertIncidentStore?.recent(
    alert.active && alert.fingerprint ? alert.fingerprint : undefined,
    options.alertIncidentHistoryLimit ?? 25
  )
  return {
    summary: {
      total: summary.total,
      byEffectiveDirective: { ...summary.byEffectiveDirective },
      topDirectives: Object.entries(summary.byEffectiveDirective)
        .map(([directive, count]) => ({ directive, count }))
        .sort((left, right) => right.count - left.count || left.directive.localeCompare(right.directive))
    },
    rateLimit: {
      limitedTotal: rateLimitSummary?.limitedTotal ?? 0,
      subjectDistribution: rateLimitSummary?.subjectDistribution ?? {
        uniqueSubjects: 0,
        topSubjects: []
      },
      ...(options.rateLimitSubjectDiagnostics
        ? { subjectDiagnostics: options.rateLimitSubjectDiagnostics }
        : {}),
      ...(rateLimitLifecycle
        ? { lifecycle: rateLimitLifecycle }
        : {})
    },
    retention,
    alert,
    alertAcknowledgement,
    alertHistory: alertHistory ?? [],
    alertIncidentHistory: alertIncidentHistory ?? [],
    trend: buildCspTelemetryTrend(trendEntries, observedAt, trendHours),
    health: {
      storage: storageHealth
    },
    recent: recent.map(toDashboardReport)
  }
}

export const isCspTelemetryOperatorAuthorized = ({
  configuredToken,
  providedToken
}: CspTelemetryOperatorAuthorizationInput): boolean => {
  const expected = configuredToken?.trim()
  if (!expected) {
    return true
  }
  return providedToken === expected
}

const toDashboardReport = (entry: StoredCspTelemetry): CspTelemetryDashboardReport => ({
  requestId: entry.report.requestId,
  receivedAt: entry.receivedAt,
  effectiveDirective: entry.report.effectiveDirective,
  violatedDirective: entry.report.violatedDirective,
  blockedUriOrigin: entry.report.blockedUriOrigin,
  documentUriOrigin: entry.report.documentUriOrigin,
  disposition: entry.report.disposition
})

const cspTelemetryStorageHealth = async (
  store: CspTelemetryStore
): Promise<CspTelemetryStorageHealth> => {
  if (!store.health) {
    return {
      backend: 'unknown',
      ok: true,
      writeFailures: 0
    }
  }
  try {
    return await store.health()
  } catch {
    return {
      backend: 'unknown',
      ok: false,
      writeFailures: 0,
      lastError: 'storage health check failed'
    }
  }
}

const buildCspTelemetryTrend = (
  entries: StoredCspTelemetry[],
  observedAt: Date,
  windowHours: number
): CspTelemetryDashboardTrend => {
  const hourMs = 60 * 60 * 1000
  const currentHour = Math.floor(observedAt.getTime() / hourMs) * hourMs
  const firstBucket = currentHour - ((windowHours - 1) * hourMs)
  const counts = new Map<number, number>()
  for (let index = 0; index < windowHours; index += 1) {
    counts.set(firstBucket + (index * hourMs), 0)
  }

  for (const entry of entries) {
    const receivedAt = Date.parse(entry.receivedAt)
    if (!Number.isFinite(receivedAt)) {
      continue
    }
    const bucket = Math.floor(receivedAt / hourMs) * hourMs
    if (counts.has(bucket)) {
      counts.set(bucket, (counts.get(bucket) ?? 0) + 1)
    }
  }

  return {
    windowHours,
    buckets: [...counts.entries()].map(([bucketStart, total]) => ({
      bucketStart: new Date(bucketStart).toISOString(),
      total
    }))
  }
}
