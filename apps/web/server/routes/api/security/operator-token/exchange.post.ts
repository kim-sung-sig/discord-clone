import { defineEventHandler, getHeader, setResponseStatus } from 'h3'
import {
  authorizeSecurityDashboardAccess,
  createSecurityDashboardAccessConfig,
  createSecurityDashboardMfaConfig,
  evaluateSecurityDashboardMfaRequirement,
  type SecurityDashboardAccessOptions,
  type SecurityDashboardMfaConfig
} from '../../../../utils/security-dashboard-access'
import {
  defaultSecurityDashboardOperatorTokenStore,
  issueSecurityDashboardOperatorToken,
  type SecurityDashboardOperatorTokenStore
} from '../../../../utils/security-dashboard-operator-token-store'

export interface SecurityDashboardOperatorTokenExchangeHandlerOptions {
  accessConfig?: SecurityDashboardAccessOptions
  mfaConfig?: SecurityDashboardMfaConfig
  operatorTokenStore?: SecurityDashboardOperatorTokenStore
  now?: () => Date
}

export const createSecurityDashboardOperatorTokenExchangeHandler = (
  options: SecurityDashboardOperatorTokenExchangeHandlerOptions = {}
) => defineEventHandler(async (event) => {
  const config = options.accessConfig ?? createSecurityDashboardAccessConfig()
  const mfaConfig = options.mfaConfig ?? createSecurityDashboardMfaConfig()
  const now = mfaConfig.now ?? options.now
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

  const principalAuthorization = await authorizeSecurityDashboardAccess({
    authorization: getHeader(event, 'authorization')
  }, {
    ...config,
    operatorToken: undefined,
    operatorTokenStore: undefined,
    allowStaticOperatorToken: false,
    ...(now ? { now } : {})
  })
  const mfa = evaluateSecurityDashboardMfaRequirement(
    principalAuthorization.principal,
    mfaConfig,
    getHeader(event, 'x-security-dashboard-mfa')
  )
  if (!mfa.satisfied) {
    setResponseStatus(event, 403)
    return {
      message: 'mfa_required'
    }
  }

  return await issueSecurityDashboardOperatorToken({
    store: options.operatorTokenStore ?? defaultSecurityDashboardOperatorTokenStore,
    actor: principalAuthorization.principal?.userId ?? 'operator-token-bootstrap',
    ...(now ? { now } : {})
  })
})

export default createSecurityDashboardOperatorTokenExchangeHandler()
