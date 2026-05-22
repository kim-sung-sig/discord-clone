import { defineEventHandler, getHeader, getQuery, setResponseStatus } from 'h3'
import { defaultCspTelemetryStore } from '../../../utils/csp-telemetry-store'
import { defaultCspRateLimitTelemetryStore } from '../../../utils/csp-rate-limit-telemetry-store'
import { defaultCspAlertTransitionStore } from '../../../utils/csp-alert-transition-store'
import { defaultCspAlertAcknowledgementStore } from '../../../utils/csp-alert-acknowledgement-store'
import { defaultCspAlertIncidentStore } from '../../../utils/csp-alert-incident-store'
import { createCspAlertThresholdOptions } from '../../../utils/csp-alert-threshold'
import { createTrustedProxyCidrs, cspRateLimitSubjectDiagnosticsFor } from '../../../utils/csp-rate-limit-subject'
import { defaultCspReportRateLimiter } from '../../../utils/csp-report-rate-limiter'
import { buildCspTelemetryDashboard } from '../../../utils/csp-telemetry-dashboard'
import { defaultSecurityDashboardOperatorTokenStore } from '../../../utils/security-dashboard-operator-token-store'
import {
  authorizeSecurityDashboardAccess,
  createSecurityDashboardAccessConfig
} from '../../../utils/security-dashboard-access'

const trustedProxyCidrs = createTrustedProxyCidrs()

export default defineEventHandler(async (event) => {
  const accessConfig = createSecurityDashboardAccessConfig()
  const authorization = await authorizeSecurityDashboardAccess({
    authorization: getHeader(event, 'authorization'),
    operatorToken: getHeader(event, 'x-operator-token')
  }, accessConfig.operatorToken
    ? {
        ...accessConfig,
        operatorTokenStore: defaultSecurityDashboardOperatorTokenStore,
        allowStaticOperatorToken: false
      }
    : accessConfig)

  if (!authorization.allowed) {
    setResponseStatus(event, 403)
    return {
      message: 'forbidden'
    }
  }

  const query = getQuery(event)
  const parsedLimit = Number.parseInt(String(query.limit ?? '25'), 10)
  return await buildCspTelemetryDashboard(defaultCspTelemetryStore, {
    recentLimit: Number.isFinite(parsedLimit) ? parsedLimit : 25,
    rateLimitTelemetryStore: defaultCspRateLimitTelemetryStore,
    rateLimitSubjectDiagnostics: cspRateLimitSubjectDiagnosticsFor({
      forwardedFor: getHeader(event, 'x-forwarded-for'),
      realIp: getHeader(event, 'x-real-ip'),
      remoteAddress: event.node.req.socket.remoteAddress,
      trustedProxyCidrs
    }),
    rateLimiter: defaultCspReportRateLimiter,
    alertTransitionStore: defaultCspAlertTransitionStore,
    alertAcknowledgementStore: defaultCspAlertAcknowledgementStore,
    alertIncidentStore: defaultCspAlertIncidentStore,
    alertThresholds: createCspAlertThresholdOptions()
  })
})
