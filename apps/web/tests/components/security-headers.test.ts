import { describe, expect, it } from 'vitest'
import { addNonceToScriptTags, htmlSecurityHeaders } from '../../server/utils/security-headers'

describe('Nuxt HTML security headers', () => {
  it('defines deployment security headers with a script nonce for rendered HTML responses', () => {
    const headers = htmlSecurityHeaders({ scriptNonce: 'nonce-test-123' })

    expect(headers['Content-Security-Policy']).toContain("default-src 'self'")
    expect(headers['Content-Security-Policy']).toContain("frame-ancestors 'none'")
    expect(headers['Content-Security-Policy']).toContain("script-src 'self' 'nonce-nonce-test-123'")
    expect(headers['Content-Security-Policy']).not.toContain("script-src 'self' 'unsafe-inline'")
    expect(headers['Content-Security-Policy']).toContain('ws://127.0.0.1:*')
    expect(headers['X-Content-Type-Options']).toBe('nosniff')
    expect(headers['X-Frame-Options']).toBe('DENY')
    expect(headers['Referrer-Policy']).toBe('no-referrer')
    expect(headers['Permissions-Policy']).toBe('camera=(), microphone=(), geolocation=()')
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
})
