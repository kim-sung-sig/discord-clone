import { defineEventHandler, getHeader, readRawBody, setResponseStatus } from 'h3'
import { handleCspReportPayloadAsync } from '../../../utils/csp-report-handler'
import { defaultCspReportRateLimiter } from '../../../utils/csp-report-rate-limiter'
import { createTrustedProxyCidrs, rateLimitSubjectFor } from '../../../utils/csp-rate-limit-subject'
import { defaultCspRateLimitTelemetryStore } from '../../../utils/csp-rate-limit-telemetry-store'
import { defaultCspTelemetryStore } from '../../../utils/csp-telemetry-store'

const requestIdFor = (incoming?: string): string =>
  incoming && /^[A-Za-z0-9._:-]{6,128}$/.test(incoming)
    ? incoming
    : `csp-${Date.now().toString(36)}`

const trustedProxyCidrs = createTrustedProxyCidrs()

export default defineEventHandler(async (event) => {
  const body = await readRawBody(event, 'utf8')
  const result = await handleCspReportPayloadAsync({
    body: body ?? '',
    contentType: getHeader(event, 'content-type'),
    requestId: requestIdFor(getHeader(event, 'x-request-id')),
    userAgent: getHeader(event, 'user-agent') ?? '',
    rateLimitSubject: rateLimitSubjectFor({
      forwardedFor: getHeader(event, 'x-forwarded-for'),
      realIp: getHeader(event, 'x-real-ip'),
      remoteAddress: event.node.req.socket.remoteAddress,
      trustedProxyCidrs
    })
  }, {
    rateLimiter: defaultCspReportRateLimiter,
    rateLimitTelemetry: defaultCspRateLimitTelemetryStore,
    telemetryStore: defaultCspTelemetryStore
  })

  if (result.accepted && result.report) {
    console.info('csp_violation_report', result.report)
  }

  setResponseStatus(event, result.statusCode)
  return null
})
