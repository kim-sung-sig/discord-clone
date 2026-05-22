import type { CspTelemetrySummary } from './csp-telemetry-store'

export interface CspAlertThresholdOptions {
  totalReportThreshold?: number
  directiveReportThreshold?: number
}

export interface CspTelemetryAlert {
  active: boolean
  fingerprint?: string
  reasons: string[]
}

export const evaluateCspTelemetryAlert = (
  summary: CspTelemetrySummary,
  options: CspAlertThresholdOptions = {}
): CspTelemetryAlert => {
  const reasons: string[] = []
  if (options.totalReportThreshold && summary.total >= options.totalReportThreshold) {
    reasons.push(`total reports ${summary.total} reached threshold ${options.totalReportThreshold}`)
  }
  if (options.directiveReportThreshold) {
    for (const [directive, count] of Object.entries(summary.byEffectiveDirective).sort(([left], [right]) => left.localeCompare(right))) {
      if (count >= options.directiveReportThreshold) {
        reasons.push(`${directive} reports ${count} reached threshold ${options.directiveReportThreshold}`)
      }
    }
  }
  return {
    active: reasons.length > 0,
    reasons
  }
}

export const createCspAlertThresholdOptions = (
  env: NodeJS.ProcessEnv = process.env
): CspAlertThresholdOptions => ({
  ...thresholdOption('totalReportThreshold', env.NUXT_CSP_ALERT_TOTAL_THRESHOLD),
  ...thresholdOption('directiveReportThreshold', env.NUXT_CSP_ALERT_DIRECTIVE_THRESHOLD)
})

const thresholdOption = (
  key: keyof CspAlertThresholdOptions,
  value: string | undefined
): CspAlertThresholdOptions => {
  const parsed = Number.parseInt(value ?? '', 10)
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return {}
  }
  return {
    [key]: parsed
  }
}
