import { defineEventHandler, getHeader, readRawBody, setResponseStatus } from 'h3'
import { handleCspReportPayload } from '../../../utils/csp-report-handler'
import { defaultCspTelemetryStore } from '../../../utils/csp-telemetry-store'

const requestIdFor = (incoming?: string): string =>
  incoming && /^[A-Za-z0-9._:-]{6,128}$/.test(incoming)
    ? incoming
    : `csp-${Date.now().toString(36)}`

export default defineEventHandler(async (event) => {
  const body = await readRawBody(event, 'utf8')
  const result = handleCspReportPayload({
    body: body ?? '',
    contentType: getHeader(event, 'content-type'),
    requestId: requestIdFor(getHeader(event, 'x-request-id')),
    userAgent: getHeader(event, 'user-agent') ?? ''
  }, {
    telemetryStore: defaultCspTelemetryStore
  })

  if (result.accepted && result.report) {
    console.info('csp_violation_report', result.report)
  }

  setResponseStatus(event, result.statusCode)
  return null
})
