import { defineEventHandler, getHeader, readBody, setResponseStatus } from 'h3'
import {
  acknowledgeCspAlert,
  cspAlertFingerprint,
  defaultCspAlertAcknowledgementStore
} from '../../../utils/csp-alert-acknowledgement-store'
import { defaultCspAlertIncidentStore } from '../../../utils/csp-alert-incident-store'
import { createCspAlertThresholdOptions, evaluateCspTelemetryAlert } from '../../../utils/csp-alert-threshold'
import { defaultCspTelemetryStore } from '../../../utils/csp-telemetry-store'
import {
  authorizeSecurityDashboardAccess,
  createSecurityDashboardAccessConfig
} from '../../../utils/security-dashboard-access'
import { defaultSecurityDashboardOperatorTokenStore } from '../../../utils/security-dashboard-operator-token-store'

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

  const body = await readBody<Partial<CspAlertAckRequest>>(event)
  const requestedFingerprint = stringValue(body.fingerprint)
  if (!requestedFingerprint) {
    setResponseStatus(event, 400)
    return {
      message: 'CSP alert fingerprint is required'
    }
  }
  const alert = evaluateCspTelemetryAlert(await defaultCspTelemetryStore.summary(), createCspAlertThresholdOptions())
  const currentFingerprint = cspAlertFingerprint(alert)
  if (requestedFingerprint !== currentFingerprint) {
    setResponseStatus(event, 409)
    return {
      message: 'CSP alert fingerprint is stale'
    }
  }
  const acknowledgedBy = authorization.principal?.userId ?? authorization.method
  try {
    return await acknowledgeCspAlert({
      alert: {
        ...alert,
        fingerprint: currentFingerprint
      },
      store: defaultCspAlertAcknowledgementStore,
      incidentStore: defaultCspAlertIncidentStore,
      reason: stringValue(body.reason) ?? '',
      acknowledgedBy,
      snoozeMinutes: numberValue(body.snoozeMinutes)
    })
  } catch (error) {
    setResponseStatus(event, 400)
    return {
      message: error instanceof Error ? error.message : 'invalid acknowledgement request'
    }
  }
})

interface CspAlertAckRequest {
  fingerprint: string
  reason: string
  snoozeMinutes?: number
}

const stringValue = (value: unknown): string | undefined =>
  typeof value === 'string' && value.trim() ? value.trim() : undefined

const numberValue = (value: unknown): number | undefined => {
  if (value === undefined || value === null || value === '') {
    return undefined
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : Number.NaN
}
