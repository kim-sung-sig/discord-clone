import { expect, test } from '@playwright/test'

test('serves CSP reporting headers and accepts redacted violation reports', async ({ page }) => {
  const response = await page.goto('/')
  expect(response?.ok()).toBe(true)

  const csp = response?.headers()['content-security-policy'] ?? ''
  expect(csp).toContain("script-src 'self' 'nonce-")
  expect(csp).not.toContain("script-src 'self' 'unsafe-inline'")
  expect(csp).toContain('report-to csp-endpoint')
  expect(csp).toContain('report-uri /api/security/csp-report')
  expect(response?.headers()['content-security-policy-report-only']).toContain("style-src 'self'")
  expect(response?.headers()['content-security-policy-report-only']).toContain('report-to csp-report-only')
  expect(response?.headers()['reporting-endpoints']).toContain('csp-endpoint="/api/security/csp-report"')

  const reportResponse = await page.request.post('/api/security/csp-report-only', {
    headers: {
      'content-type': 'application/reports+json',
      'x-request-id': 'e2e-csp-report-1'
    },
    data: [
      {
        type: 'csp-violation',
        url: 'http://127.0.0.1/channels/general?token=secret',
        body: {
          blockedURL: 'inline',
          violatedDirective: 'style-src',
          effectiveDirective: 'style-src',
          disposition: 'report',
          sample: 'color:red'
        }
      }
    ]
  })

  expect(reportResponse.status()).toBe(204)
  expect(await reportResponse.text()).toBe('')
})
