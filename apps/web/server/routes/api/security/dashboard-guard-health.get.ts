import { defineEventHandler, setResponseStatus } from 'h3'
import {
  buildSecurityDashboardGuardHealth,
  createSecurityDashboardAccessConfig,
  probeSecurityDashboardBackendAuthCheck
} from '../../../utils/security-dashboard-access'
import { defaultSecurityDashboardOperatorTokenStore } from '../../../utils/security-dashboard-operator-token-store'

export default defineEventHandler(async (event) => {
  const accessConfig = createSecurityDashboardAccessConfig()
  const backendCheck = await probeSecurityDashboardBackendAuthCheck(accessConfig)
  const health = buildSecurityDashboardGuardHealth(accessConfig.operatorToken
    ? {
        ...accessConfig,
        operatorTokenStore: defaultSecurityDashboardOperatorTokenStore,
        allowStaticOperatorToken: false
      }
    : accessConfig,
  backendCheck.configured ? backendCheck : undefined)
  if (health.status === 'fail-closed') {
    setResponseStatus(event, 503)
  }
  return health
})
