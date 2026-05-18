import {
  normalizeCspReportPayload,
  shouldAcceptCspReport,
  type CspReportNormalizationResult
} from './security-headers'
import { defaultCspTelemetryStore, type CspTelemetryStore } from './csp-telemetry-store'

export interface CspReportHandlerInput {
  body: string
  contentType?: string
  requestId: string
  userAgent: string
}

export interface CspReportHandlerResult extends CspReportNormalizationResult {
  statusCode: number
}

export interface CspReportHandlerOptions {
  telemetryStore?: CspTelemetryStore
  now?: () => Date
}

export const handleCspReportPayload = (
  input: CspReportHandlerInput,
  options: CspReportHandlerOptions = {}
): CspReportHandlerResult => {
  if (!shouldAcceptCspReport(input.contentType, input.body)) {
    return {
      accepted: false,
      statusCode: 204,
      reason: 'unsupported or too large'
    }
  }

  const normalized = normalizeCspReportPayload(input.body, {
    requestId: input.requestId,
    userAgent: input.userAgent
  })

  if (normalized.accepted && normalized.report) {
    const store = options.telemetryStore ?? defaultCspTelemetryStore
    store.record(normalized.report, options.now?.() ?? new Date())
  }

  return {
    ...normalized,
    statusCode: 204
  }
}
