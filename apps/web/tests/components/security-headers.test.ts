import { describe, expect, it } from 'vitest'
import { htmlSecurityHeaders } from '../../server/utils/security-headers'

describe('Nuxt HTML security headers', () => {
  it('defines deployment security headers for rendered HTML responses', () => {
    const headers = htmlSecurityHeaders()

    expect(headers['Content-Security-Policy']).toContain("default-src 'self'")
    expect(headers['Content-Security-Policy']).toContain("frame-ancestors 'none'")
    expect(headers['Content-Security-Policy']).toContain("script-src 'self' 'unsafe-inline'")
    expect(headers['Content-Security-Policy']).toContain('ws://127.0.0.1:*')
    expect(headers['X-Content-Type-Options']).toBe('nosniff')
    expect(headers['X-Frame-Options']).toBe('DENY')
    expect(headers['Referrer-Policy']).toBe('no-referrer')
    expect(headers['Permissions-Policy']).toBe('camera=(), microphone=(), geolocation=()')
  })
})
