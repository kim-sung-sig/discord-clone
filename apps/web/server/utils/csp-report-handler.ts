import {
  normalizeCspReportPayload,
  shouldAcceptCspReport,
  type CspReportNormalizationResult
} from './security-headers'
import { defaultCspTelemetryStore, type CspTelemetryStore } from './csp-telemetry-store'
import { type CspReportRateLimiter } from './csp-report-rate-limiter'
import { type CspRateLimitTelemetryStore } from './csp-rate-limit-telemetry-store'

export interface CspReportHandlerInput {
  body: string
  contentType?: string
  requestId: string
  userAgent: string
  rateLimitSubject?: string
}

export interface CspReportHandlerResult extends CspReportNormalizationResult {
  statusCode: number
}

export interface CspReportHandlerOptions {
  telemetryStore?: CspTelemetryStore
  rateLimiter?: CspReportRateLimiter
  rateLimitTelemetry?: CspRateLimitTelemetryStore
  now?: () => Date
}

export const handleCspReportPayload = (
  input: CspReportHandlerInput,
  options: CspReportHandlerOptions = {}
): CspReportHandlerResult => {
  const receivedAt = options.now?.() ?? new Date()
  if (options.rateLimiter) {
    const decision = options.rateLimiter.consume({
      subject: input.rateLimitSubject ?? 'unknown',
      at: receivedAt
    })
    if (isPromise(decision)) {
      throw new Error('async CSP report rate limiter requires handleCspReportPayloadAsync')
    }
    if (!decision.allowed) {
      const telemetryResult = recordLimited(input, options, receivedAt, decision.resetAt)
      if (isPromise(telemetryResult)) {
        throw new Error('async CSP rate-limit telemetry store requires handleCspReportPayloadAsync')
      }
      return {
        accepted: false,
        statusCode: 204,
        reason: 'rate limited'
      }
    }
  }

  const normalized = normalizeCspReport(input)
  storeNormalizedReport(normalized, options, receivedAt)
  return normalized
}

export const handleCspReportPayloadAsync = async (
  input: CspReportHandlerInput,
  options: CspReportHandlerOptions = {}
): Promise<CspReportHandlerResult> => {
  const receivedAt = options.now?.() ?? new Date()
  if (options.rateLimiter) {
    const decision = await options.rateLimiter.consume({
      subject: input.rateLimitSubject ?? 'unknown',
      at: receivedAt
    })
    if (!decision.allowed) {
      await recordLimited(input, options, receivedAt, decision.resetAt)
      return {
        accepted: false,
        statusCode: 204,
        reason: 'rate limited'
      }
    }
  }

  const normalized = normalizeCspReport(input)
  await storeNormalizedReportAsync(normalized, options, receivedAt)
  return normalized
}

const normalizeCspReport = (
  input: CspReportHandlerInput
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

  return {
    ...normalized,
    statusCode: 204
  }
}

const storeNormalizedReport = (
  normalized: CspReportHandlerResult,
  options: CspReportHandlerOptions,
  receivedAt: Date
): void => {
  if (!normalized.accepted || !normalized.report) {
    return
  }
  const store = options.telemetryStore ?? defaultCspTelemetryStore
  const result = store.record(normalized.report, receivedAt)
  if (isPromise(result)) {
    throw new Error('async CSP telemetry store requires handleCspReportPayloadAsync')
  }
}

const storeNormalizedReportAsync = async (
  normalized: CspReportHandlerResult,
  options: CspReportHandlerOptions,
  receivedAt: Date
): Promise<void> => {
  if (!normalized.accepted || !normalized.report) {
    return
  }
  const store = options.telemetryStore ?? defaultCspTelemetryStore
  await store.record(normalized.report, receivedAt)
}

const isPromise = <T>(value: T | Promise<T>): value is Promise<T> =>
  Boolean(value) && typeof (value as Promise<T>).then === 'function'

const recordLimited = (
  input: CspReportHandlerInput,
  options: CspReportHandlerOptions,
  receivedAt: Date,
  resetAt: string
): void | Promise<void> => {
  return options.rateLimitTelemetry?.recordLimited({
    subject: input.rateLimitSubject ?? 'unknown',
    at: receivedAt,
    resetAt
  })
}
