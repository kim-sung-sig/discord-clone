import { defineEventHandler, getHeader, setResponseStatus } from 'h3'
import {
  authorizeSecurityDashboardAccess,
  createSecurityDashboardAccessConfig
} from '../../../../utils/security-dashboard-access'
import {
  defaultSecurityDashboardOperatorTokenStore,
  issueSecurityDashboardOperatorToken
} from '../../../../utils/security-dashboard-operator-token-store'

export default defineEventHandler(async (event) => {
  const config = createSecurityDashboardAccessConfig()
  const bootstrap = await authorizeSecurityDashboardAccess({
    operatorToken: getHeader(event, 'x-operator-token')
  }, {
    operatorToken: config.operatorToken,
    allowStaticOperatorToken: true,
    requireConfiguredGuard: true
  })

  if (!bootstrap.allowed) {
    setResponseStatus(event, 403)
    return {
      message: 'forbidden'
    }
  }

  return await issueSecurityDashboardOperatorToken({
    store: defaultSecurityDashboardOperatorTokenStore,
    actor: 'operator-token-bootstrap'
  })
})
