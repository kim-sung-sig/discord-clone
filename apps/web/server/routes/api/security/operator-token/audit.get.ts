import { defineEventHandler, getHeader, getQuery, setResponseStatus } from 'h3'
import {
  authorizeSecurityDashboardAccess,
  createSecurityDashboardAccessConfig,
  type SecurityDashboardAccessOptions
} from '../../../../utils/security-dashboard-access'
import {
  defaultSecurityDashboardOperatorTokenStore,
  type SecurityDashboardOperatorTokenStore
} from '../../../../utils/security-dashboard-operator-token-store'

export interface SecurityDashboardOperatorTokenAuditHandlerOptions {
  accessConfig?: SecurityDashboardAccessOptions
  operatorTokenStore?: SecurityDashboardOperatorTokenStore
}

export const createSecurityDashboardOperatorTokenAuditHandler = (
  options: SecurityDashboardOperatorTokenAuditHandlerOptions = {}
) => defineEventHandler(async (event) => {
  const operatorTokenStore = options.operatorTokenStore ?? defaultSecurityDashboardOperatorTokenStore
  const accessConfig = options.accessConfig ?? createSecurityDashboardAccessConfig()
  const authorization = await authorizeSecurityDashboardAccess({
    authorization: getHeader(event, 'authorization'),
    operatorToken: getHeader(event, 'x-operator-token')
  }, {
    ...accessConfig,
    operatorTokenStore,
    allowStaticOperatorToken: false
  })

  if (!authorization.allowed) {
    setResponseStatus(event, 403)
    return {
      message: 'forbidden'
    }
  }

  const query = getQuery(event)
  const parsedLimit = Number.parseInt(String(query.limit ?? '25'), 10)
  const entries = await operatorTokenStore.audit?.(
    Number.isFinite(parsedLimit) ? parsedLimit : 25
  )

  return {
    entries: entries ?? []
  }
})

export default createSecurityDashboardOperatorTokenAuditHandler()
