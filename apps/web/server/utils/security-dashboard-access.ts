import { createHmac, timingSafeEqual } from 'node:crypto'
import type {
  SecurityDashboardOperatorTokenStore
} from './security-dashboard-operator-token-store'

export type SecurityDashboardAccessMethod = 'backend' | 'jwt' | 'operator-token' | 'local-dev-open' | 'denied'

export interface SecurityDashboardAccessInput {
  authorization?: string
  operatorToken?: string
}

export interface SecurityDashboardPrincipal {
  userId?: string
  roles: string[]
  scopes: string[]
  admin?: boolean
  mfa?: SecurityDashboardMfaEvidence
}

export interface SecurityDashboardAccessResult {
  allowed: boolean
  method: SecurityDashboardAccessMethod
  reason?: string
  principal?: SecurityDashboardPrincipal
}

export interface SecurityDashboardPrincipalPolicy {
  adminUserIds?: string[]
  adminRoles?: string[]
  adminScopes?: string[]
}

export type SecurityDashboardBackendVerifier = (
  authorization: string
) => Promise<unknown>

export interface SecurityDashboardAccessOptions extends SecurityDashboardPrincipalPolicy {
  authCheckUrl?: string
  jwtSecret?: string
  operatorToken?: string
  operatorTokenStore?: SecurityDashboardOperatorTokenStore
  allowStaticOperatorToken?: boolean
  requireConfiguredGuard?: boolean
  backendVerifier?: SecurityDashboardBackendVerifier
  fetcher?: typeof fetch
  now?: () => Date
}

export interface SecurityDashboardAccessConfig extends SecurityDashboardAccessOptions {
}

export interface SecurityDashboardMfaEvidence {
  verified: boolean
  methods: string[]
  verifiedAt?: string
}

export interface SecurityDashboardMfaConfig {
  requireForOperatorActions: boolean
  maxAgeMinutes: number
  allowTestMfaHeader: boolean
  now?: () => Date
}

export interface SecurityDashboardMfaEvaluation {
  satisfied: boolean
  reason?: string
}

export type SecurityDashboardGuardHealthStatus = 'ready' | 'local-dev-open' | 'fail-closed'

export interface SecurityDashboardGuardHealth {
  configured: boolean
  requireConfiguredGuard: boolean
  status: SecurityDashboardGuardHealthStatus
  methods: {
    backend: boolean
    jwt: boolean
    operatorToken: boolean
    adminUserIds: boolean
    adminRoles: boolean
    adminScopes: boolean
  }
  backendCheck?: SecurityDashboardBackendCheckHealth
  warnings: string[]
}

export interface SecurityDashboardBackendCheckHealth {
  configured: boolean
  reachable: boolean
  statusCode?: number
  checkedAt: string
}

interface JwtVerificationResult {
  principal?: SecurityDashboardPrincipal
  reason?: string
}

const BEARER_PREFIX = 'Bearer '
const DEFAULT_MFA_MAX_AGE_MINUTES = 15
const TEST_MFA_HEADER_VALUE = 'verified'
const MFA_METHOD_HINTS = new Set(['mfa', 'otp', 'totp', 'webauthn', 'u2f', 'fido', 'fido2', 'hwk', 'sms', 'push'])

export const createSecurityDashboardAccessConfig = (
  env: NodeJS.ProcessEnv = process.env
): SecurityDashboardAccessConfig => ({
  authCheckUrl: env.NUXT_SECURITY_DASHBOARD_AUTH_CHECK_URL?.trim(),
  jwtSecret: env.NUXT_SECURITY_DASHBOARD_JWT_SECRET?.trim(),
  operatorToken: env.NUXT_SECURITY_DASHBOARD_TOKEN?.trim(),
  adminUserIds: parseList(env.NUXT_SECURITY_DASHBOARD_ADMIN_USER_IDS),
  adminRoles: parseList(env.NUXT_SECURITY_DASHBOARD_ADMIN_ROLES),
  adminScopes: parseList(env.NUXT_SECURITY_DASHBOARD_ADMIN_SCOPES),
  requireConfiguredGuard: env.NODE_ENV === 'production' || env.NUXT_SECURITY_DASHBOARD_REQUIRE_GUARD === 'true'
})

export const createSecurityDashboardMfaConfig = (
  env: NodeJS.ProcessEnv = process.env
): SecurityDashboardMfaConfig => {
  const production = env.NODE_ENV === 'production'
  return {
    requireForOperatorActions: production || env.NUXT_SECURITY_DASHBOARD_REQUIRE_MFA !== 'false',
    maxAgeMinutes: parsePositiveInteger(
      env.NUXT_SECURITY_DASHBOARD_MFA_MAX_AGE_MINUTES,
      DEFAULT_MFA_MAX_AGE_MINUTES,
      120
    ),
    allowTestMfaHeader: !production && env.NUXT_SECURITY_DASHBOARD_ALLOW_TEST_MFA_HEADER === 'true'
  }
}

export const evaluateSecurityDashboardMfaRequirement = (
  principal: SecurityDashboardPrincipal | undefined,
  config: SecurityDashboardMfaConfig,
  testMfaHeader?: string
): SecurityDashboardMfaEvaluation => {
  if (!config.requireForOperatorActions) {
    return { satisfied: true }
  }
  if (config.allowTestMfaHeader && testMfaHeader?.trim() === TEST_MFA_HEADER_VALUE) {
    return { satisfied: true }
  }

  const evidence = principal?.mfa
  const verifiedAt = evidence?.verifiedAt ? new Date(evidence.verifiedAt).getTime() : Number.NaN
  const now = (config.now?.() ?? new Date()).getTime()
  const maxAgeMs = Math.max(1, config.maxAgeMinutes) * 60_000
  if (
    evidence?.verified
    && Number.isFinite(verifiedAt)
    && verifiedAt <= now
    && now - verifiedAt <= maxAgeMs
  ) {
    return { satisfied: true }
  }
  return {
    satisfied: false,
    reason: 'fresh MFA evidence is required'
  }
}

export const authorizeSecurityDashboardAccess = async (
  input: SecurityDashboardAccessInput,
  options: SecurityDashboardAccessOptions
): Promise<SecurityDashboardAccessResult> => {
  const backendVerifier = options.backendVerifier ?? backendVerifierFromOptions(options)
  let lastDenial: SecurityDashboardAccessResult | undefined
  if (backendVerifier && input.authorization) {
    const backendResult = await authorizeWithBackend(input.authorization, backendVerifier, options)
    if (backendResult.allowed) {
      return backendResult
    }
    lastDenial = backendResult
    if (backendResult.reason !== 'backend principal is not authorized') {
      return backendResult
    }
  }

  if (options.jwtSecret?.trim() && input.authorization) {
    const jwtResult = authorizeWithJwt(input.authorization, options)
    if (jwtResult.allowed) {
      return jwtResult
    }
    lastDenial = jwtResult
    if (jwtResult.reason !== 'JWT principal is not authorized') {
      return jwtResult
    }
  }

  if (options.operatorTokenStore && input.operatorToken) {
    const tokenResult = await options.operatorTokenStore.verify(input.operatorToken, options.now?.() ?? new Date())
    if (tokenResult.valid) {
      return {
        allowed: true,
        method: 'operator-token',
        principal: {
          userId: tokenResult.actor,
          roles: [],
          scopes: []
        }
      }
    }
    lastDenial = {
      allowed: false,
      method: 'denied',
      reason: tokenResult.reason ?? 'operator token is invalid'
    }
  }

  if (options.operatorToken?.trim() && options.allowStaticOperatorToken !== false) {
    return authorizeWithOperatorToken(input.operatorToken, options.operatorToken)
  }

  if (!hasConfiguredGuard(options)) {
    if (options.requireConfiguredGuard) {
      return {
        allowed: false,
        method: 'denied',
        reason: 'security dashboard guard is not configured'
      }
    }
    return {
      allowed: true,
      method: 'local-dev-open'
    }
  }

  if (lastDenial) {
    return lastDenial
  }

  return {
    allowed: false,
    method: 'denied',
    reason: 'no configured guard authorized the request'
  }
}

export const buildSecurityDashboardGuardHealth = (
  options: SecurityDashboardAccessOptions,
  backendCheck?: SecurityDashboardBackendCheckHealth
): SecurityDashboardGuardHealth => {
  const methods = {
    backend: Boolean(options.authCheckUrl?.trim() || options.backendVerifier),
    jwt: Boolean(options.jwtSecret?.trim()),
    operatorToken: Boolean(options.operatorToken?.trim() || options.operatorTokenStore),
    adminUserIds: (options.adminUserIds?.length ?? 0) > 0,
    adminRoles: (options.adminRoles?.length ?? 0) > 0,
    adminScopes: (options.adminScopes?.length ?? 0) > 0
  }
  const configured = Object.values(methods).some(Boolean)
  const requireConfiguredGuard = options.requireConfiguredGuard === true
  const warnings = configured || !requireConfiguredGuard
    ? []
    : ['security dashboard guard is required but not configured']

  return {
    configured,
    requireConfiguredGuard,
    status: configured ? 'ready' : requireConfiguredGuard ? 'fail-closed' : 'local-dev-open',
    methods,
    ...(backendCheck ? { backendCheck } : {}),
    warnings
  }
}

export const probeSecurityDashboardBackendAuthCheck = async (
  options: Pick<SecurityDashboardAccessOptions, 'authCheckUrl' | 'fetcher' | 'now'>
): Promise<SecurityDashboardBackendCheckHealth> => {
  const checkedAt = (options.now?.() ?? new Date()).toISOString()
  const authCheckUrl = options.authCheckUrl?.trim()
  if (!authCheckUrl) {
    return {
      configured: false,
      reachable: false,
      checkedAt
    }
  }
  try {
    const response = await (options.fetcher ?? globalThis.fetch)(authCheckUrl, {
      method: 'GET',
      headers: {
        Authorization: 'Bearer probe'
      }
    })
    return {
      configured: true,
      reachable: [200, 401, 403].includes(response.status),
      statusCode: response.status,
      checkedAt
    }
  } catch {
    return {
      configured: true,
      reachable: false,
      checkedAt
    }
  }
}

export const evaluateSecurityDashboardPrincipal = (
  value: unknown,
  policy: SecurityDashboardPrincipalPolicy
): SecurityDashboardAccessResult => {
  const principal = normalizePrincipal(value)
  if (!principal) {
    return {
      allowed: false,
      method: 'denied',
      reason: 'backend principal is not authorized'
    }
  }
  if (principalIsAuthorized(principal, policy)) {
    return {
      allowed: true,
      method: 'backend',
      principal
    }
  }
  return {
    allowed: false,
    method: 'denied',
    reason: 'backend principal is not authorized',
    principal
  }
}

const authorizeWithBackend = async (
  authorization: string,
  backendVerifier: SecurityDashboardBackendVerifier,
  options: SecurityDashboardPrincipalPolicy
): Promise<SecurityDashboardAccessResult> => {
  try {
    const principalResponse = await backendVerifier(authorization)
    const result = evaluateSecurityDashboardPrincipal(principalResponse, options)
    return result.allowed ? { ...result, method: 'backend' } : result
  } catch {
    return {
      allowed: false,
      method: 'denied',
      reason: 'backend verification failed'
    }
  }
}

const authorizeWithJwt = (
  authorization: string,
  options: SecurityDashboardAccessOptions
): SecurityDashboardAccessResult => {
  const token = bearerToken(authorization)
  if (!token) {
    return {
      allowed: false,
      method: 'denied',
      reason: 'JWT bearer token is required'
    }
  }

  const verification = verifyHs256Jwt(token, options.jwtSecret ?? '', options.now ?? (() => new Date()))
  if (!verification.principal) {
    return {
      allowed: false,
      method: 'denied',
      reason: verification.reason
    }
  }
  if (!principalIsAuthorized(verification.principal, options)) {
    return {
      allowed: false,
      method: 'denied',
      reason: 'JWT principal is not authorized',
      principal: verification.principal
    }
  }
  return {
    allowed: true,
    method: 'jwt',
    principal: verification.principal
  }
}

const authorizeWithOperatorToken = (
  providedToken: string | undefined,
  configuredToken: string
): SecurityDashboardAccessResult => {
  if (providedToken === configuredToken) {
    return {
      allowed: true,
      method: 'operator-token'
    }
  }
  return {
    allowed: false,
    method: 'denied',
    reason: 'operator token is invalid'
  }
}

const backendVerifierFromOptions = (
  options: SecurityDashboardAccessOptions
): SecurityDashboardBackendVerifier | undefined => {
  const authCheckUrl = options.authCheckUrl?.trim()
  if (!authCheckUrl) {
    return undefined
  }
  const fetcher = options.fetcher ?? globalThis.fetch
  return async (authorization: string) => {
    const response = await fetcher(authCheckUrl, {
      method: 'GET',
      headers: {
        Authorization: authorization
      }
    })
    if (!response.ok) {
      throw new Error(`security dashboard auth check failed with ${response.status}`)
    }
    return response.json()
  }
}

const verifyHs256Jwt = (
  token: string,
  secret: string,
  now: () => Date
): JwtVerificationResult => {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return { reason: 'JWT is invalid' }
  }

  const [headerPart, payloadPart, signaturePart] = parts
  const header = parseJwtPart(headerPart)
  if (!header || header.alg !== 'HS256') {
    return { reason: 'JWT algorithm is invalid' }
  }

  const expectedSignature = createHmac('sha256', secret)
    .update(`${headerPart}.${payloadPart}`)
    .digest('base64url')
  if (!safeEqual(expectedSignature, signaturePart)) {
    return { reason: 'JWT signature is invalid' }
  }

  const payload = parseJwtPart(payloadPart)
  if (!payload) {
    return { reason: 'JWT is invalid' }
  }
  const expiresAt = typeof payload.exp === 'number' ? payload.exp : Number(payload.exp)
  if (!Number.isFinite(expiresAt) || Math.floor(now().getTime() / 1000) >= expiresAt) {
    return { reason: 'JWT is expired' }
  }

  const mfa = mfaEvidenceFromClaims(payload)
  return {
    principal: {
      userId: stringValue(payload.sub ?? payload.userId ?? payload.id),
      roles: arrayValues(payload.roles ?? payload.role),
      scopes: scopeValues(payload.scope ?? payload.scopes),
      ...(mfa ? { mfa } : {})
    }
  }
}

const normalizePrincipal = (value: unknown): SecurityDashboardPrincipal | undefined => {
  if (!isRecord(value)) {
    return undefined
  }
  const mfa = mfaEvidenceFromClaims(value)
  if (value.admin === true || value.isAdmin === true) {
    return {
      userId: stringValue(value.id ?? value.userId ?? value.sub ?? recordValue(value.user, 'id')),
      roles: arrayValues(value.roles ?? value.role),
      scopes: scopeValues(value.scope ?? value.scopes),
      admin: true,
      ...(mfa ? { mfa } : {})
    }
  }
  const userId = stringValue(value.id ?? value.userId ?? value.sub ?? recordValue(value.user, 'id'))
  const roles = arrayValues(value.roles ?? value.role ?? recordValue(value.user, 'roles'))
  const scopes = scopeValues(value.scope ?? value.scopes ?? recordValue(value.user, 'scope') ?? recordValue(value.user, 'scopes'))

  if (!userId && roles.length === 0 && scopes.length === 0) {
    return undefined
  }
  return {
    userId,
    roles,
    scopes,
    ...(mfa ? { mfa } : {})
  }
}

const principalIsAuthorized = (
  principal: SecurityDashboardPrincipal,
  policy: SecurityDashboardPrincipalPolicy
): boolean => {
  const adminUserIds = setOf(policy.adminUserIds)
  const adminRoles = setOf(policy.adminRoles)
  const adminScopes = setOf(policy.adminScopes)

  if (principal.admin) {
    return true
  }
  if (principal.userId && adminUserIds.has(principal.userId)) {
    return true
  }
  if (principal.roles.some((role) => adminRoles.has(role))) {
    return true
  }
  return principal.scopes.some((scope) => adminScopes.has(scope))
}

const hasConfiguredGuard = (options: SecurityDashboardAccessOptions): boolean =>
  Boolean(
    options.authCheckUrl?.trim()
    || options.jwtSecret?.trim()
    || options.operatorToken?.trim()
    || options.operatorTokenStore
    || (options.adminUserIds?.length ?? 0) > 0
    || (options.adminRoles?.length ?? 0) > 0
    || (options.adminScopes?.length ?? 0) > 0
    || options.backendVerifier
  )

const bearerToken = (authorization: string): string | undefined => {
  if (!authorization.startsWith(BEARER_PREFIX)) {
    return undefined
  }
  return authorization.slice(BEARER_PREFIX.length)
}

const parseJwtPart = (value: string): Record<string, unknown> | undefined => {
  try {
    const parsed = JSON.parse(Buffer.from(value, 'base64url').toString('utf8'))
    return isRecord(parsed) ? parsed : undefined
  } catch {
    return undefined
  }
}

const safeEqual = (left: string, right: string): boolean => {
  const leftBuffer = Buffer.from(left)
  const rightBuffer = Buffer.from(right)
  return leftBuffer.length === rightBuffer.length && timingSafeEqual(leftBuffer, rightBuffer)
}

const parseList = (value: string | undefined): string[] =>
  (value ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)

const parsePositiveInteger = (value: string | undefined, fallback: number, max: number): number => {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  if (!Number.isFinite(parsed) || parsed < 1) {
    return fallback
  }
  return Math.min(parsed, max)
}

const setOf = (values: string[] | undefined): Set<string> => new Set(values ?? [])

const arrayValues = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.map(stringValue).filter((item): item is string => Boolean(item))
  }
  const single = stringValue(value)
  return single ? [single] : []
}

const scopeValues = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return arrayValues(value)
  }
  const single = stringValue(value)
  return single ? single.split(/\s+/).filter(Boolean) : []
}

const mfaEvidenceFromClaims = (value: Record<string, unknown>): SecurityDashboardMfaEvidence | undefined => {
  const user = recordObject(value.user)
  const methods = uniqueValues([
    ...scopeValues(value.amr ?? user?.amr),
    ...scopeValues(value.mfaMethods ?? value.mfa_methods ?? user?.mfaMethods ?? user?.mfa_methods),
    ...scopeValues(value.acr ?? user?.acr)
  ])
  const verified = booleanValue(
    value.mfaVerified
      ?? value.mfa_verified
      ?? value.mfa
      ?? user?.mfaVerified
      ?? user?.mfa_verified
      ?? user?.mfa
  ) || methods.some((method) => MFA_METHOD_HINTS.has(method.toLowerCase()) || method.toLowerCase().includes('mfa'))
  const verifiedAt = timestampClaimToIso(
    value.authTime
      ?? value.auth_time
      ?? value.mfaVerifiedAt
      ?? value.mfa_verified_at
      ?? value.iat
      ?? user?.authTime
      ?? user?.auth_time
      ?? user?.mfaVerifiedAt
      ?? user?.mfa_verified_at
      ?? user?.iat
  )

  if (!verified && methods.length === 0 && !verifiedAt) {
    return undefined
  }
  return {
    verified,
    methods,
    ...(verifiedAt ? { verifiedAt } : {})
  }
}

const uniqueValues = (values: string[]): string[] => Array.from(new Set(values))

const booleanValue = (value: unknown): boolean =>
  value === true || (typeof value === 'string' && value.trim().toLowerCase() === 'true')

const timestampClaimToIso = (value: unknown): string | undefined => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return timestampNumberToIso(value)
  }
  const text = stringValue(value)
  if (!text) {
    return undefined
  }
  if (/^\d+$/.test(text)) {
    return timestampNumberToIso(Number.parseInt(text, 10))
  }
  const parsed = Date.parse(text)
  return Number.isFinite(parsed) ? new Date(parsed).toISOString() : undefined
}

const timestampNumberToIso = (value: number): string | undefined => {
  const milliseconds = value > 1_000_000_000_000 ? value : value * 1000
  const date = new Date(milliseconds)
  return Number.isFinite(date.getTime()) ? date.toISOString() : undefined
}

const stringValue = (value: unknown): string | undefined =>
  typeof value === 'string' && value.trim() ? value.trim() : undefined

const isRecord = (value: unknown): value is Record<string, unknown> =>
  Boolean(value) && typeof value === 'object' && !Array.isArray(value)

const recordValue = (value: unknown, key: string): unknown =>
  isRecord(value) ? value[key] : undefined

const recordObject = (value: unknown): Record<string, unknown> | undefined =>
  isRecord(value) ? value : undefined
