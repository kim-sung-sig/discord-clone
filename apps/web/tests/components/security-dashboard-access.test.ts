import { createHmac } from 'node:crypto'
import { createApp, toWebHandler } from 'h3'
import { describe, expect, it } from 'vitest'
import { createSecurityDashboardOperatorTokenAuditHandler } from '../../server/routes/api/security/operator-token/audit.get'
import {
  authorizeSecurityDashboardAccess,
  buildSecurityDashboardGuardHealth,
  createSecurityDashboardAccessConfig,
  evaluateSecurityDashboardPrincipal,
  probeSecurityDashboardBackendAuthCheck
} from '../../server/utils/security-dashboard-access'
import {
  createDefaultSecurityDashboardOperatorTokenStore,
  hashSecurityDashboardOperatorTokenForTests,
  InMemorySecurityDashboardOperatorTokenStore,
  PostgresSecurityDashboardOperatorTokenStore,
  issueSecurityDashboardOperatorToken
} from '../../server/utils/security-dashboard-operator-token-store'

describe('security dashboard access guard', () => {
  it('authorizes through backend verification before JWT or operator token fallbacks', async () => {
    const result = await authorizeSecurityDashboardAccess({
      authorization: 'Bearer backend-token',
      operatorToken: 'ops-token'
    }, {
      adminUserIds: ['admin-user'],
      jwtSecret: 'local-jwt-secret-with-enough-length',
      operatorToken: 'ops-token',
      backendVerifier: async (authorization) => {
        expect(authorization).toBe('Bearer backend-token')
        return {
          id: 'admin-user',
          roles: []
        }
      }
    })

    expect(result).toMatchObject({
      allowed: true,
      method: 'backend',
      principal: {
        userId: 'admin-user'
      }
    })
  })

  it('denies a backend-verified user without an admin role, scope, flag, or allowlisted id', async () => {
    const result = await authorizeSecurityDashboardAccess({
      authorization: 'Bearer member-token'
    }, {
      adminUserIds: ['admin-user'],
      adminRoles: ['ADMIN'],
      backendVerifier: async () => ({
        id: 'member-user',
        roles: ['MEMBER']
      })
    })

    expect(result).toMatchObject({
      allowed: false,
      method: 'denied',
      reason: 'backend principal is not authorized'
    })
  })

  it('authorizes a valid HS256 JWT only when the subject is allowlisted', async () => {
    const secret = 'local-jwt-secret-with-enough-length'
    const token = signJwt({ sub: 'admin-user', iat: 1779148800, exp: 1779235200 }, secret)

    const result = await authorizeSecurityDashboardAccess({
      authorization: `Bearer ${token}`
    }, {
      jwtSecret: secret,
      adminUserIds: ['admin-user'],
      now: () => new Date('2026-05-19T00:00:00.000Z')
    })

    expect(result).toMatchObject({
      allowed: true,
      method: 'jwt',
      principal: {
        userId: 'admin-user'
      }
    })
  })

  it('rejects invalid, expired, and non-admin JWTs', async () => {
    const secret = 'local-jwt-secret-with-enough-length'
    const expired = signJwt({ sub: 'admin-user', iat: 1779062400, exp: 1779148799 }, secret)
    const nonAdmin = signJwt({ sub: 'member-user', iat: 1779148800, exp: 1779235200 }, secret)
    const wrongSignature = signJwt({ sub: 'admin-user', iat: 1779148800, exp: 1779235200 }, 'wrong-secret-with-enough-length')
    const options = {
      jwtSecret: secret,
      adminUserIds: ['admin-user'],
      now: () => new Date('2026-05-19T00:00:00.000Z')
    }

    await expect(authorizeSecurityDashboardAccess({ authorization: `Bearer ${expired}` }, options))
      .resolves.toMatchObject({ allowed: false, reason: 'JWT is expired' })
    await expect(authorizeSecurityDashboardAccess({ authorization: `Bearer ${nonAdmin}` }, options))
      .resolves.toMatchObject({ allowed: false, reason: 'JWT principal is not authorized' })
    await expect(authorizeSecurityDashboardAccess({ authorization: `Bearer ${wrongSignature}` }, options))
      .resolves.toMatchObject({ allowed: false, reason: 'JWT signature is invalid' })
  })

  it('keeps the operator token as the final configured fallback', async () => {
    await expect(authorizeSecurityDashboardAccess({
      operatorToken: 'ops-token'
    }, {
      operatorToken: 'ops-token'
    })).resolves.toMatchObject({
      allowed: true,
      method: 'operator-token'
    })

    await expect(authorizeSecurityDashboardAccess({
      operatorToken: 'wrong-token'
    }, {
      operatorToken: 'ops-token'
    })).resolves.toMatchObject({
      allowed: false,
      method: 'denied',
      reason: 'operator token is invalid'
    })
  })

  it('authorizes server-issued ephemeral operator tokens without storing raw token values', async () => {
    const store = new InMemorySecurityDashboardOperatorTokenStore()
    const issued = await issueSecurityDashboardOperatorToken({
      store,
      actor: 'bootstrap-operator',
      now: () => new Date('2026-05-21T00:00:00.000Z')
    })

    expect(issued.token).toMatch(/^sdo_[A-Za-z0-9_-]{32,}$/)
    expect(issued.expiresAt).toBe('2026-05-21T00:15:00.000Z')
    expect(JSON.stringify(store.audit())).not.toContain(issued.token)

    await expect(authorizeSecurityDashboardAccess({
      operatorToken: issued.token
    }, {
      operatorTokenStore: store,
      now: () => new Date('2026-05-21T00:05:00.000Z')
    })).resolves.toMatchObject({
      allowed: true,
      method: 'operator-token',
      principal: {
        userId: 'bootstrap-operator'
      }
    })

    await store.revoke({
      token: issued.token,
      actor: 'bootstrap-operator',
      reason: 'operator cleared session',
      now: new Date('2026-05-21T00:06:00.000Z')
    })
    await expect(authorizeSecurityDashboardAccess({
      operatorToken: issued.token
    }, {
      operatorTokenStore: store,
      now: () => new Date('2026-05-21T00:07:00.000Z')
    })).resolves.toMatchObject({
      allowed: false,
      reason: 'operator token is invalid'
    })
  })

  it('serves operator token audit entries through a guarded route without raw token values', async () => {
    const store = new InMemorySecurityDashboardOperatorTokenStore()
    const revoked = await issueSecurityDashboardOperatorToken({
      store,
      actor: 'operator-token-bootstrap',
      now: () => new Date('2026-05-21T00:00:00.000Z')
    })
    await store.revoke({
      token: revoked.token,
      actor: 'security-admin',
      reason: 'operator cleared session',
      now: new Date('2026-05-21T00:06:00.000Z')
    })
    const active = await issueSecurityDashboardOperatorToken({
      store,
      actor: 'operator-token-bootstrap',
      now: () => new Date('2999-05-21T00:07:00.000Z')
    })

    const app = createApp()
    app.use('/api/security/operator-token/audit', createSecurityDashboardOperatorTokenAuditHandler({
      operatorTokenStore: store
    }))
    const response = await toWebHandler(app)(new Request('http://localhost/api/security/operator-token/audit?limit=3', {
      headers: {
        'x-operator-token': active.token
      }
    }))
    const payload = await response.json()

    expect(response.status).toBe(200)
    expect(payload.entries).toEqual([
      {
        action: 'issued',
        actor: 'operator-token-bootstrap',
        at: '2999-05-21T00:07:00.000Z',
        tokenId: hashSecurityDashboardOperatorTokenForTests(active.token).slice(0, 12)
      },
      {
        action: 'revoked',
        actor: 'security-admin',
        at: '2026-05-21T00:06:00.000Z',
        reason: 'operator cleared session',
        tokenId: hashSecurityDashboardOperatorTokenForTests(revoked.token).slice(0, 12)
      },
      {
        action: 'issued',
        actor: 'operator-token-bootstrap',
        at: '2026-05-21T00:00:00.000Z',
        tokenId: hashSecurityDashboardOperatorTokenForTests(revoked.token).slice(0, 12)
      }
    ])
    for (const entry of payload.entries) {
      expect(entry.tokenId).toMatch(/^[a-f0-9]{12}$/)
      expect(entry.tokenId).not.toHaveLength(64)
    }
    expect(JSON.stringify(payload)).not.toContain(active.token)
    expect(JSON.stringify(payload)).not.toContain(revoked.token)
    expect(JSON.stringify(payload)).not.toContain(hashSecurityDashboardOperatorTokenForTests(active.token))
    expect(JSON.stringify(payload)).not.toContain(hashSecurityDashboardOperatorTokenForTests(revoked.token))

    const forbidden = await toWebHandler(app)(new Request('http://localhost/api/security/operator-token/audit'))
    expect(forbidden.status).toBe(403)
  })

  it('allows a signed admin JWT to read operator token audit without an operator token header', async () => {
    const store = new InMemorySecurityDashboardOperatorTokenStore()
    const issued = await issueSecurityDashboardOperatorToken({
      store,
      actor: 'operator-token-bootstrap',
      now: () => new Date('2026-05-21T00:00:00.000Z')
    })
    const jwtSecret = 'local-jwt-secret-with-enough-length'
    const adminJwt = signJwt({ sub: 'admin-user', iat: 1779148800, exp: 32503680000 }, jwtSecret)
    const app = createApp()
    app.use('/api/security/operator-token/audit', createSecurityDashboardOperatorTokenAuditHandler({
      operatorTokenStore: store,
      accessConfig: {
        jwtSecret,
        adminUserIds: ['admin-user']
      }
    }))

    const response = await toWebHandler(app)(new Request('http://localhost/api/security/operator-token/audit?limit=1', {
      headers: {
        Authorization: `Bearer ${adminJwt}`
      }
    }))
    const payload = await response.json()

    expect(response.status).toBe(200)
    expect(payload.entries).toEqual([
      expect.objectContaining({
        action: 'issued',
        tokenId: hashSecurityDashboardOperatorTokenForTests(issued.token).slice(0, 12)
      })
    ])
    expect(JSON.stringify(payload)).not.toContain(issued.token)
  })

  it('selects a durable Postgres operator token store when a database URL is configured', async () => {
    const memoryStore = createDefaultSecurityDashboardOperatorTokenStore({})
    expect(memoryStore).toBeInstanceOf(InMemorySecurityDashboardOperatorTokenStore)

    const postgresStore = createDefaultSecurityDashboardOperatorTokenStore({
      databaseUrl: 'postgres://dev_user:dev_password@127.0.0.1:15432/discord'
    })
    expect(postgresStore).toBeInstanceOf(PostgresSecurityDashboardOperatorTokenStore)
    await postgresStore.close?.()
  })

  it('preserves local development open mode only when no guard is configured', async () => {
    await expect(authorizeSecurityDashboardAccess({}, {})).resolves.toMatchObject({
      allowed: true,
      method: 'local-dev-open'
    })
  })

  it('fails closed when production requires a configured dashboard guard', async () => {
    await expect(authorizeSecurityDashboardAccess({}, {
      requireConfiguredGuard: true
    })).resolves.toMatchObject({
      allowed: false,
      method: 'denied',
      reason: 'security dashboard guard is not configured'
    })
  })

  it('reads production guard enforcement from environment configuration', () => {
    expect(createSecurityDashboardAccessConfig({
      NODE_ENV: 'production'
    }).requireConfiguredGuard).toBe(true)
    expect(createSecurityDashboardAccessConfig({
      NODE_ENV: 'development',
      NUXT_SECURITY_DASHBOARD_REQUIRE_GUARD: 'true'
    }).requireConfiguredGuard).toBe(true)
    expect(createSecurityDashboardAccessConfig({
      NODE_ENV: 'development'
    }).requireConfiguredGuard).toBe(false)
  })

  it('evaluates flexible backend principal response shapes', () => {
    expect(evaluateSecurityDashboardPrincipal({ admin: true }, {})).toMatchObject({ allowed: true })
    expect(evaluateSecurityDashboardPrincipal({ user: { id: 'admin-user' } }, {
      adminUserIds: ['admin-user']
    })).toMatchObject({ allowed: true })
    expect(evaluateSecurityDashboardPrincipal({ roles: ['SECURITY_ADMIN'] }, {
      adminRoles: ['SECURITY_ADMIN']
    })).toMatchObject({ allowed: true })
    expect(evaluateSecurityDashboardPrincipal({ scope: 'openid security:read' }, {
      adminScopes: ['security:read']
    })).toMatchObject({ allowed: true })
    expect(evaluateSecurityDashboardPrincipal({ id: 'member-user', roles: ['MEMBER'] }, {
      adminUserIds: ['admin-user'],
      adminRoles: ['SECURITY_ADMIN']
    })).toMatchObject({ allowed: false })
  })

  it('summarizes guard configuration health without exposing configured secrets', () => {
    expect(buildSecurityDashboardGuardHealth(createSecurityDashboardAccessConfig({
      NODE_ENV: 'production'
    }))).toEqual({
      configured: false,
      requireConfiguredGuard: true,
      status: 'fail-closed',
      methods: {
        backend: false,
        jwt: false,
        operatorToken: false,
        adminUserIds: false,
        adminRoles: false,
        adminScopes: false
      },
      warnings: ['security dashboard guard is required but not configured']
    })

    const configured = buildSecurityDashboardGuardHealth(createSecurityDashboardAccessConfig({
      NODE_ENV: 'production',
      NUXT_SECURITY_DASHBOARD_TOKEN: 'secret-token',
      NUXT_SECURITY_DASHBOARD_JWT_SECRET: 'jwt-secret',
      NUXT_SECURITY_DASHBOARD_ADMIN_ROLES: 'SECURITY_ADMIN'
    }))

    expect(configured).toEqual({
      configured: true,
      requireConfiguredGuard: true,
      status: 'ready',
      methods: {
        backend: false,
        jwt: true,
        operatorToken: true,
        adminUserIds: false,
        adminRoles: true,
        adminScopes: false
      },
      warnings: []
    })
    expect(JSON.stringify(configured)).not.toContain('secret-token')
    expect(JSON.stringify(configured)).not.toContain('jwt-secret')
  })

  it('probes configured backend auth check reachability without exposing the URL or token', async () => {
    const result = await probeSecurityDashboardBackendAuthCheck({
      authCheckUrl: 'https://backend.example.test/api/users/@me?token=secret',
      now: () => new Date('2026-05-21T00:00:00.000Z'),
      fetcher: async (input, init) => {
        expect(input).toBe('https://backend.example.test/api/users/@me?token=secret')
        expect(init?.headers).toEqual({ Authorization: 'Bearer probe' })
        return new Response('{}', { status: 401 })
      }
    })

    expect(result).toEqual({
      configured: true,
      reachable: true,
      statusCode: 401,
      checkedAt: '2026-05-21T00:00:00.000Z'
    })
    expect(JSON.stringify(result)).not.toContain('backend.example')
    expect(JSON.stringify(result)).not.toContain('secret')
  })

  it('reports backend auth check probe failures without leaking error details', async () => {
    const result = await probeSecurityDashboardBackendAuthCheck({
      authCheckUrl: 'https://backend.example.test/api/users/@me',
      now: () => new Date('2026-05-21T00:00:00.000Z'),
      fetcher: async () => {
        throw new Error('connect ECONNREFUSED https://backend.example.test?password=secret')
      }
    })

    expect(result).toEqual({
      configured: true,
      reachable: false,
      checkedAt: '2026-05-21T00:00:00.000Z'
    })
    expect(JSON.stringify(result)).not.toContain('secret')
    expect(JSON.stringify(result)).not.toContain('ECONNREFUSED')
  })
})

const signJwt = (payload: Record<string, unknown>, secret: string): string => {
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }))
  const body = base64Url(JSON.stringify(payload))
  const unsigned = `${header}.${body}`
  const signature = createHmac('sha256', secret).update(unsigned).digest('base64url')
  return `${unsigned}.${signature}`
}

const base64Url = (value: string): string => Buffer.from(value).toString('base64url')
