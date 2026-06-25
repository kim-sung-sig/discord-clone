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
  type SecurityDashboardOperatorTokenStore
} from '../../../../utils/security-dashboard-operator-token-store'

export interface SecurityDashboardOperatorTokenRevokeHandlerOptions {
  accessConfig?: SecurityDashboardAccessOptions
  mfaConfig?: SecurityDashboardMfaConfig
  operatorTokenStore?: SecurityDashboardOperatorTokenStore
  now?: () => Date
}

export const createSecurityDashboardOperatorTokenRevokeHandler = (
  options: SecurityDashboardOperatorTokenRevokeHandlerOptions = {}
) => defineEventHandler(async (event) => {
  const operatorTokenStore = options.operatorTokenStore ?? defaultSecurityDashboardOperatorTokenStore
  const accessConfig = options.accessConfig ?? createSecurityDashboardAccessConfig()
  const mfaConfig = options.mfaConfig ?? createSecurityDashboardMfaConfig()
  const now = mfaConfig.now ?? options.now ?? (() => new Date())
  const operatorToken = getHeader(event, 'x-operator-token')
  const tokenAuthorization = await authorizeSecurityDashboardAccess({
    operatorToken
  }, {
    operatorTokenStore,
    allowStaticOperatorToken: false,
    now
  })

  if (!tokenAuthorization.allowed || !operatorToken) {
    setResponseStatus(event, 403)
    return {
      message: 'forbidden'
    }
  }

  const principalAuthorization = await authorizeSecurityDashboardAccess({
    authorization: getHeader(event, 'authorization')
  }, {
    ...accessConfig,
    operatorToken: undefined,
    operatorTokenStore: undefined,
    allowStaticOperatorToken: false,
    now
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

  const revoked = await operatorTokenStore.revoke({
    token: operatorToken,
    actor: principalAuthorization.principal?.userId ?? tokenAuthorization.principal?.userId ?? tokenAuthorization.method,
    reason: 'operator requested revoke',
    now: now()
  })

  if (!revoked) {
    setResponseStatus(event, 404)
    return {
      message: 'operator_token_not_found'
    }
  }

  return {
    revoked: true
  }
})

export default createSecurityDashboardOperatorTokenRevokeHandler()
