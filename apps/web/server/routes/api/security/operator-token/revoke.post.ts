import { defineEventHandler, getHeader, setResponseStatus } from 'h3'
import {
  authorizeSecurityDashboardAccess,
  createSecurityDashboardAccessConfig
} from '../../../../utils/security-dashboard-access'
import {
  defaultSecurityDashboardOperatorTokenStore
} from '../../../../utils/security-dashboard-operator-token-store'

export default defineEventHandler(async (event) => {
  const operatorToken = getHeader(event, 'x-operator-token')
  const authorization = await authorizeSecurityDashboardAccess({
    authorization: getHeader(event, 'authorization'),
    operatorToken
  }, {
    ...createSecurityDashboardAccessConfig(),
    operatorTokenStore: defaultSecurityDashboardOperatorTokenStore,
    allowStaticOperatorToken: false
  })

  if (!authorization.allowed || !operatorToken) {
    setResponseStatus(event, 403)
    return {
      message: 'forbidden'
    }
  }

  await defaultSecurityDashboardOperatorTokenStore.revoke({
    token: operatorToken,
    actor: authorization.principal?.userId ?? authorization.method,
    reason: 'operator requested revoke',
    now: new Date()
  })

  return {
    revoked: true
  }
})
