import { mountSuspended } from '@nuxt/test-utils/runtime'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import SecurityPage from '../../pages/security.vue'
import { useAuthStore } from '../../stores/auth'

const jsonResponse = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' }
  })

const flushDashboard = async () => {
  for (let index = 0; index < 6; index += 1) {
    await Promise.resolve()
    await nextTick()
  }
}

describe('browser security dashboard', () => {
  let pinia: ReturnType<typeof createPinia>

  beforeEach(() => {
    pinia = createPinia()
    setActivePinia(pinia)
    window.localStorage.clear()
    window.sessionStorage.clear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('renders CSP telemetry summary, top directives, and recent sanitized reports', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 3,
        byEffectiveDirective: {
          'style-src': 2,
          'script-src': 1
        },
        topDirectives: [
          { directive: 'style-src', count: 2 },
          { directive: 'script-src', count: 1 }
        ]
      },
      recent: [
        {
          requestId: 'req-dashboard-3',
          receivedAt: '2026-05-19T00:00:02.000Z',
          effectiveDirective: 'style-src',
          violatedDirective: "style-src 'self'",
          blockedUriOrigin: 'https://evil.example',
          documentUriOrigin: 'https://app.discord-clone.local',
          disposition: 'report'
        }
      ]
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(wrapper.get('[data-testid="security-dashboard"]').text()).toContain('Browser security')
    expect(wrapper.get('[data-testid="csp-total"]').text()).toContain('3')
    expect(wrapper.get('[data-testid="csp-directive-style-src"]').text()).toContain('style-src')
    expect(wrapper.get('[data-testid="csp-directive-style-src"]').text()).toContain('2')
    expect(wrapper.get('[data-testid="csp-report-req-dashboard-3"]').text()).toContain('https://evil.example')
    expect(wrapper.get('[data-testid="csp-report-req-dashboard-3"]').text()).toContain('https://app.discord-clone.local')
    expect(wrapper.text()).not.toContain('secret')
    expect(wrapper.text()).not.toContain('document.cookie')
  })

  it('renders an empty state when no CSP telemetry exists', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 0,
        byEffectiveDirective: {},
        topDirectives: []
      },
      recent: []
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(wrapper.get('[data-testid="csp-empty-state"]').text()).toContain('No CSP reports recorded')
  })

  it('renders an active CSP alert banner with threshold reasons', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 12,
        byEffectiveDirective: {
          'script-src': 9
        },
        topDirectives: [
          { directive: 'script-src', count: 9 }
        ]
      },
      recent: [],
      alert: {
        active: true,
        fingerprint: 'alert-fingerprint-1',
        reasons: [
          'total CSP reports 12 exceeded threshold 10',
          'script-src reports 9 exceeded threshold 5'
        ]
      },
      alertAcknowledgement: {
        fingerprint: 'alert-fingerprint-1',
        status: 'unacknowledged'
      }
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    const banner = wrapper.get('[data-testid="csp-alert-banner"]')
    expect(banner.text()).toContain('CSP alert active')
    expect(banner.text()).toContain('Unacknowledged')
    expect(banner.text()).toContain('total CSP reports 12 exceeded threshold 10')
    expect(banner.text()).toContain('script-src reports 9 exceeded threshold 5')
  })

  it('acknowledges an active CSP alert with reason and snooze without exposing the operator token', async () => {
    const calls: Array<{ input: string, init?: RequestInit }> = []
    window.sessionStorage.setItem('dc_security_dashboard_operator_token', 'ops-token')
    vi.stubGlobal('fetch', async (input, init) => {
      calls.push({ input: String(input), init })
      if (String(input).includes('/api/security/csp-alert-ack')) {
        return jsonResponse({
          fingerprint: 'alert-fingerprint-ack',
          status: 'snoozed',
          reason: 'Known deployment',
          acknowledgedBy: 'operator-token',
          acknowledgedAt: '2026-05-20T00:00:00.000Z',
          snoozeUntil: '2026-05-20T00:15:00.000Z'
        })
      }
      return jsonResponse({
        summary: {
          total: 12,
          byEffectiveDirective: {
            'script-src': 9
          },
          topDirectives: [
            { directive: 'script-src', count: 9 }
          ]
        },
        recent: [],
        alert: {
          active: true,
          fingerprint: 'alert-fingerprint-ack',
          reasons: ['script-src reports 9 reached threshold 5']
        },
        alertAcknowledgement: calls.some((call) => call.input.includes('/api/security/csp-alert-ack'))
          ? {
              fingerprint: 'alert-fingerprint-ack',
              status: 'snoozed',
              reason: 'Known deployment',
              acknowledgedBy: 'operator-token',
              acknowledgedAt: '2026-05-20T00:00:00.000Z',
              snoozeUntil: '2026-05-20T00:15:00.000Z'
            }
          : {
              fingerprint: 'alert-fingerprint-ack',
              status: 'unacknowledged'
            }
      })
    })

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    await wrapper.get('[data-testid="csp-alert-ack-reason"]').setValue('Known deployment')
    await wrapper.get('[data-testid="csp-alert-snooze-minutes"]').setValue('15')
    await wrapper.get('[data-testid="csp-alert-ack-form"]').trigger('submit')
    await flushDashboard()

    expect(wrapper.vm.alertAckError).toBe('')
    expect(calls.map((call) => call.input)).toEqual(expect.arrayContaining(['/api/security/csp-alert-ack']))
    const ackCall = calls.find((call) => call.input.includes('/api/security/csp-alert-ack'))
    expect(ackCall?.init?.method).toBe('POST')
    expect(ackCall?.init?.headers).toEqual({
      'content-type': 'application/json',
      'x-operator-token': 'ops-token'
    })
    expect(JSON.parse(String(ackCall?.init?.body))).toEqual({
      fingerprint: 'alert-fingerprint-ack',
      reason: 'Known deployment',
      snoozeMinutes: 15
    })
    expect(wrapper.get('[data-testid="csp-alert-ack-status"]').text()).toContain('Snoozed')
    expect(wrapper.text()).not.toContain('ops-token')
  })

  it('renders persisted CSP alert transition history', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 12,
        byEffectiveDirective: {
          'script-src': 9
        },
        topDirectives: [
          { directive: 'script-src', count: 9 }
        ]
      },
      recent: [],
      alert: {
        active: true,
        reasons: ['script-src reports 9 reached threshold 5']
      },
      alertHistory: [
        {
          active: true,
          observedAt: '2026-05-20T00:00:03.000Z',
          reasons: ['script-src reports 9 reached threshold 5']
        },
        {
          active: false,
          observedAt: '2026-05-20T00:00:00.000Z',
          reasons: []
        }
      ]
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(wrapper.get('[data-testid="csp-alert-history"]').text()).toContain('Alert history')
    expect(wrapper.get('[data-testid="csp-alert-history-2026-05-20T00:00:03.000Z"]').text()).toContain('Active')
    expect(wrapper.get('[data-testid="csp-alert-history-2026-05-20T00:00:03.000Z"]').text()).toContain('script-src reports 9 reached threshold 5')
    expect(wrapper.get('[data-testid="csp-alert-history-2026-05-20T00:00:00.000Z"]').text()).toContain('Cleared')
  })

  it('renders CSP alert incident lifecycle history without exposing alert reasons', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 12,
        byEffectiveDirective: {
          'script-src': 9
        },
        topDirectives: [
          { directive: 'script-src', count: 9 }
        ]
      },
      recent: [],
      alert: {
        active: true,
        reasons: ['script-src reports 9 reached threshold 5'],
        fingerprint: 'csp-incident-fingerprint'
      },
      alertIncidentHistory: [
        {
          fingerprint: 'csp-incident-fingerprint',
          eventType: 'snoozed',
          status: 'snoozed',
          actor: 'operator-user',
          assignedTo: 'operator-user',
          reason: 'Known deployment',
          occurredAt: '2026-05-20T00:00:03.000Z',
          snoozeUntil: '2026-05-20T00:15:03.000Z'
        }
      ]
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    const history = wrapper.get('[data-testid="csp-alert-incident-history"]')
    expect(history.text()).toContain('Incident lifecycle')
    expect(history.text()).toContain('Snoozed')
    expect(history.text()).toContain('operator-user')
    expect(history.text()).toContain('Known deployment')
    expect(history.text()).not.toContain('script-src reports 9 reached threshold 5')
  })

  it('renders CSP telemetry retention discard totals and breakdown counts', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 2,
        byEffectiveDirective: {
          'style-src': 1,
          'script-src': 1
        },
        topDirectives: [
          { directive: 'script-src', count: 1 },
          { directive: 'style-src', count: 1 }
        ]
      },
      recent: [],
      retention: {
        discardedTotal: 3,
        discardedByAge: 2,
        discardedByMaxEntries: 1
      }
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(wrapper.get('[data-testid="csp-retention-discarded"]').text()).toContain('3')
    expect(wrapper.get('[data-testid="csp-retention-discarded-by-age"]').text()).toContain('2')
    expect(wrapper.get('[data-testid="csp-retention-discarded-by-max-entries"]').text()).toContain('1')
    expect(wrapper.text()).not.toContain('req-retention')
    expect(wrapper.text()).not.toContain('https://evil.example')
  })

  it('renders a secret-safe CSP telemetry trend chart', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 4,
        byEffectiveDirective: {
          'style-src': 3,
          'script-src': 1
        },
        topDirectives: [
          { directive: 'style-src', count: 3 },
          { directive: 'script-src', count: 1 }
        ]
      },
      trend: {
        windowHours: 6,
        buckets: [
          { bucketStart: '2026-05-19T00:00:00.000Z', total: 0 },
          { bucketStart: '2026-05-19T01:00:00.000Z', total: 1 },
          { bucketStart: '2026-05-19T02:00:00.000Z', total: 0 },
          { bucketStart: '2026-05-19T03:00:00.000Z', total: 3 },
          { bucketStart: '2026-05-19T04:00:00.000Z', total: 0 },
          { bucketStart: '2026-05-19T05:00:00.000Z', total: 0 }
        ]
      },
      recent: []
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    const panel = wrapper.get('[data-testid="csp-trend-chart"]')
    expect(panel.text()).toContain('CSP trend')
    expect(wrapper.get('[data-testid="csp-trend-window"]').text()).toContain('6h')
    expect(wrapper.get('[data-testid="csp-trend-bucket-2026-05-19T01:00:00.000Z"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="csp-trend-bucket-2026-05-19T03:00:00.000Z"]').text()).toContain('3')
    expect(wrapper.text()).not.toContain('token=secret')
    expect(wrapper.text()).not.toContain('203.0.113')
  })

  it('renders CSP rate-limit totals for empty and non-zero states', async () => {
    let callCount = 0
    vi.stubGlobal('fetch', async () => {
      callCount += 1
      return jsonResponse({
        summary: {
          total: callCount === 1 ? 0 : 4,
          byEffectiveDirective: {},
          topDirectives: []
        },
        rateLimit: {
          limitedTotal: callCount === 1 ? 0 : 7
        },
        recent: []
      })
    })

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(wrapper.get('[data-testid="csp-rate-limit-limited"]').text()).toContain('0')

    await wrapper.vm.loadDashboard()
    await flushDashboard()

    expect(wrapper.get('[data-testid="csp-rate-limit-limited"]').text()).toContain('7')
  })

  it('renders secret-safe CSP rate-limit subject diagnostics', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 0,
        byEffectiveDirective: {},
        topDirectives: []
      },
      recent: [],
      rateLimit: {
        limitedTotal: 0,
        subjectDiagnostics: {
          source: 'x-forwarded-for',
          subjectHashPrefix: 'abc123def456',
          trustedProxyConfigured: true,
          trustedProxyMatched: true,
          trustedProxyRuleCount: 1,
          forwardedForPresent: true,
          realIpPresent: true
        }
      }
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    const panel = wrapper.get('[data-testid="csp-rate-limit-subject-diagnostics"]')
    expect(panel.text()).toContain('Subject diagnostics')
    expect(panel.text()).toContain('x-forwarded-for')
    expect(panel.text()).toContain('Matched')
    expect(panel.text()).toContain('abc123def456')
    expect(panel.text()).not.toContain('203.0.113')
    expect(panel.text()).not.toContain('10.0.0.25')
  })

  it('renders privacy-reviewed CSP rate-limit subject distribution without subject identifiers', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 0,
        byEffectiveDirective: {},
        topDirectives: []
      },
      recent: [],
      rateLimit: {
        limitedTotal: 6,
        subjectDistribution: {
          uniqueSubjects: 3,
          topSubjects: [
            { rank: 1, count: 4, share: 0.6667 },
            { rank: 2, count: 2, share: 0.3333 }
          ]
        }
      }
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    const panel = wrapper.get('[data-testid="csp-rate-limit-subject-distribution"]')
    expect(panel.text()).toContain('Subject distribution')
    expect(wrapper.get('[data-testid="csp-rate-limit-unique-subjects"]').text()).toContain('3')
    expect(wrapper.get('[data-testid="csp-rate-limit-distribution-total"]').text()).toContain('6')
    expect(wrapper.get('[data-testid="csp-rate-limit-subject-rank-1"]').text()).toContain('Subject #1')
    expect(wrapper.get('[data-testid="csp-rate-limit-subject-rank-1"]').text()).toContain('4')
    expect(wrapper.get('[data-testid="csp-rate-limit-subject-rank-1"]').text()).toContain('67%')
    expect(panel.text()).not.toContain('203.0.113')
    expect(panel.text()).not.toContain('abc123def456')
  })

  it('renders Redis CSP limiter lifecycle metrics without exposing connection secrets', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 0,
        byEffectiveDirective: {},
        topDirectives: []
      },
      recent: [],
      rateLimit: {
        limitedTotal: 0,
        lifecycle: {
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
        }
      }
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    const panel = wrapper.get('[data-testid="csp-rate-limit-lifecycle"]')
    expect(panel.text()).toContain('Redis lifecycle')
    expect(wrapper.get('[data-testid="csp-rate-limit-backend"]').text()).toContain('redis')
    expect(wrapper.get('[data-testid="csp-rate-limit-fail-closed"]').text()).toContain('2')
    expect(wrapper.get('[data-testid="csp-rate-limit-connect-failures"]').text()).toContain('1')
    expect(wrapper.get('[data-testid="csp-rate-limit-error-events"]').text()).toContain('1')
    expect(wrapper.text()).not.toContain('redis://')
    expect(wrapper.text()).not.toContain('dev_password')
  })

  it('renders CSP telemetry storage health and write failures', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 0,
        byEffectiveDirective: {},
        topDirectives: []
      },
      health: {
        storage: {
          backend: 'postgres',
          ok: false,
          writeFailures: 2,
          lastWriteFailureAt: '2026-05-20T00:00:00.000Z',
          lastError: 'connection unavailable'
        }
      },
      recent: []
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(wrapper.get('[data-testid="csp-telemetry-storage-health"]').text()).toContain('Postgres degraded')
    expect(wrapper.get('[data-testid="csp-telemetry-write-failures"]').text()).toContain('2')
    expect(wrapper.text()).not.toContain('postgres://')
    expect(wrapper.text()).not.toContain('dev_password')
  })

  it('renders dashboard guard health without exposing configured secrets', async () => {
    let callCount = 0
    vi.stubGlobal('fetch', async () => {
      callCount += 1
      if (callCount === 2) {
        return jsonResponse({
          configured: true,
          requireConfiguredGuard: true,
          status: 'ready',
          methods: {
            backend: true,
            jwt: false,
            operatorToken: true,
            adminUserIds: false,
            adminRoles: true,
            adminScopes: false
          },
          warnings: [],
          backendCheck: {
            configured: true,
            reachable: true,
            statusCode: 401,
            checkedAt: '2026-05-21T00:00:00.000Z'
          }
        })
      }

      return jsonResponse({
        summary: {
          total: 0,
          byEffectiveDirective: {},
          topDirectives: []
        },
        recent: []
      })
    })

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    const panel = wrapper.get('[data-testid="dashboard-guard-health"]')
    expect(panel.text()).toContain('Dashboard guard health')
    expect(panel.text()).toContain('Ready')
    expect(panel.text()).toContain('Backend')
    expect(panel.text()).toContain('Operator token')
    expect(panel.text()).toContain('Admin roles')
    expect(wrapper.get('[data-testid="dashboard-backend-auth-probe"]').text()).toContain('Reachable')
    expect(wrapper.get('[data-testid="dashboard-backend-auth-probe-status"]').text()).toContain('401')
    expect(panel.text()).not.toContain('secret')
  })

  it('does not render the CSP alert banner when alert state is inactive', async () => {
    vi.stubGlobal('fetch', async () => jsonResponse({
      summary: {
        total: 1,
        byEffectiveDirective: {
          'style-src': 1
        },
        topDirectives: [
          { directive: 'style-src', count: 1 }
        ]
      },
      recent: [],
      alert: {
        active: false,
        reasons: []
      }
    }))

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(wrapper.find('[data-testid="csp-alert-banner"]').exists()).toBe(false)
  })

  it('forwards the signed-in bearer token and optional operator token to the telemetry API', async () => {
    const calls: RequestInit[] = []
    const auth = useAuthStore()
    auth.accessToken = 'backend-access-token'
    window.sessionStorage.setItem('dc_security_dashboard_operator_token', 'ops-token')
    vi.stubGlobal('fetch', async (_input, init) => {
      calls.push(init ?? {})
      return jsonResponse({
        summary: {
          total: 0,
          byEffectiveDirective: {},
          topDirectives: []
        },
        recent: []
      })
    })

    await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(calls[0]?.headers).toEqual({
      Authorization: 'Bearer backend-access-token',
      'x-operator-token': 'ops-token'
    })
  })

  it('renders operator token audit entries with token hash prefixes but not raw tokens', async () => {
    const calls: Array<{ input: string, init?: RequestInit }> = []
    window.sessionStorage.setItem('dc_security_dashboard_operator_token', 'sdo_saved-dashboard-token')
    vi.stubGlobal('fetch', async (input, init) => {
      calls.push({ input: String(input), init })
      if (String(input).includes('/api/security/operator-token/audit')) {
        return jsonResponse({
          entries: [
            {
              action: 'revoked',
              tokenId: 'abc123def456',
              actor: 'security-admin',
              at: '2026-05-21T00:06:00.000Z',
              reason: 'operator cleared session'
            },
            {
              action: 'issued',
              tokenId: 'fedcba654321',
              actor: 'operator-token-bootstrap',
              at: '2026-05-21T00:00:00.000Z'
            }
          ]
        })
      }
      return jsonResponse({
        summary: {
          total: 0,
          byEffectiveDirective: {},
          topDirectives: []
        },
        recent: []
      })
    })

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    const panel = wrapper.get('[data-testid="operator-token-audit"]')
    expect(panel.text()).toContain('Operator token audit')
    expect(panel.text()).toContain('Revoked')
    expect(panel.text()).toContain('abc123def456')
    expect(panel.text()).toContain('security-admin')
    expect(panel.text()).toContain('operator cleared session')
    expect(panel.text()).toContain('Issued')
    expect(panel.text()).toContain('fedcba654321')
    expect(wrapper.text()).not.toContain('sdo_saved-dashboard-token')

    const auditCall = calls.find((call) => call.input.includes('/api/security/operator-token/audit'))
    expect(auditCall?.init?.headers).toEqual({
      'x-operator-token': 'sdo_saved-dashboard-token'
    })
  })

  it('lets an operator save a token and retry telemetry from the dashboard', async () => {
    const calls: Array<{ input: string, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input, init) => {
      calls.push({ input: String(input), init })
      if (String(input).includes('/api/security/operator-token/exchange')) {
        return jsonResponse({
          token: 'sdo_retry-token',
          expiresAt: '2026-05-21T00:15:00.000Z',
          tokenId: 'abc123def456'
        })
      }
      if (calls.length === 1) {
        return jsonResponse({ error: 'forbidden' }, 403)
      }
      return jsonResponse({
        summary: {
          total: 1,
          byEffectiveDirective: {
            'style-src': 1
          },
          topDirectives: [
            { directive: 'style-src', count: 1 }
          ]
        },
        recent: [],
        alert: {
          active: false,
          reasons: []
        }
      })
    })

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    expect(wrapper.get('[data-testid="csp-error-state"]').text()).toContain('Unable to load')

    await wrapper.get('[data-testid="operator-token-input"]').setValue('ops-token')
    await wrapper.get('[data-testid="operator-token-form"]').trigger('submit')
    await flushDashboard()

    expect(window.sessionStorage.getItem('dc_security_dashboard_operator_token')).toBe('sdo_retry-token')
    const telemetryRetry = calls.findLast((call) => call.input.includes('/api/security/csp-telemetry'))
    expect(telemetryRetry?.init?.headers).toEqual({
      'x-operator-token': 'sdo_retry-token'
    })
    expect(wrapper.get('[data-testid="csp-total"]').text()).toContain('1')
  })

  it('exchanges a bootstrap operator token for a short-lived dashboard token without rendering either token', async () => {
    const calls: Array<{ input: string, init?: RequestInit }> = []
    vi.stubGlobal('fetch', async (input, init) => {
      calls.push({ input: String(input), init })
      if (String(input).includes('/api/security/operator-token/exchange')) {
        return jsonResponse({
          token: 'sdo_issued-dashboard-token',
          expiresAt: '2026-05-21T00:15:00.000Z',
          tokenId: 'abc123def456'
        })
      }
      return jsonResponse({
        summary: {
          total: 1,
          byEffectiveDirective: {
            'style-src': 1
          },
          topDirectives: [
            { directive: 'style-src', count: 1 }
          ]
        },
        recent: []
      })
    })

    const wrapper = await mountSuspended(SecurityPage, {
      global: { plugins: [pinia] }
    })
    await flushDashboard()

    await wrapper.get('[data-testid="operator-token-input"]').setValue('bootstrap-token')
    await wrapper.get('[data-testid="operator-token-form"]').trigger('submit')
    await flushDashboard()

    const exchangeCall = calls.find((call) => call.input.includes('/api/security/operator-token/exchange'))
    expect(exchangeCall?.init?.method).toBe('POST')
    expect(exchangeCall?.init?.headers).toEqual({
      'content-type': 'application/json',
      'x-operator-token': 'bootstrap-token'
    })
    expect(window.sessionStorage.getItem('dc_security_dashboard_operator_token')).toBe('sdo_issued-dashboard-token')
    expect(wrapper.get('[data-testid="operator-token-expiry"]').text()).toContain('2026-05-21T00:15:00.000Z')
    expect(wrapper.text()).not.toContain('bootstrap-token')
    expect(wrapper.text()).not.toContain('sdo_issued-dashboard-token')
  })
})
