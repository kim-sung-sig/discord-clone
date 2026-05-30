import { describe, expect, it } from 'vitest'
import {
  PostgresCspTelemetryStore
} from '../../server/utils/csp-telemetry-store'
import {
  PostgresCspRateLimitTelemetryStore
} from '../../server/utils/csp-rate-limit-telemetry-store'
import {
  PostgresCspAlertTransitionStore
} from '../../server/utils/csp-alert-transition-store'
import {
  PostgresCspAlertAcknowledgementStore
} from '../../server/utils/csp-alert-acknowledgement-store'
import {
  PostgresCspAlertIncidentStore
} from '../../server/utils/csp-alert-incident-store'
import {
  PostgresSecurityDashboardOperatorTokenStore
} from '../../server/utils/security-dashboard-operator-token-store'

const databaseUrl = process.env.NUXT_CSP_TELEMETRY_POSTGRES_URL
  ?? 'postgres://dev_user:dev_password@127.0.0.1:15432/discord'

describe.runIf(process.env.NUXT_RUN_POSTGRES_TESTS === 'true')('Postgres CSP telemetry store', () => {
  it('persists sanitized CSP telemetry in the central Postgres database with retention metrics', async () => {
    const store = new PostgresCspTelemetryStore({
      databaseUrl,
      maxEntries: 2,
      maxAgeMs: 60_000
    })
    try {
      await store.clearForTests()

      await store.record(sampleReport('req-postgres-old', 'style-src'), new Date('2026-05-19T00:00:00.000Z'))
      await store.record(sampleReport('req-postgres-recent-1', 'script-src'), new Date('2026-05-19T00:01:30.000Z'))
      await store.record(sampleReport('req-postgres-recent-2', 'img-src'), new Date('2026-05-19T00:02:00.000Z'))
      await store.record(sampleReport('req-postgres-recent-3', 'connect-src'), new Date('2026-05-19T00:02:30.000Z'))

      expect((await store.recent()).map((entry) => entry.report.requestId)).toEqual([
        'req-postgres-recent-3',
        'req-postgres-recent-2'
      ])
      expect(await store.summary()).toEqual({
        total: 2,
        byEffectiveDirective: {
          'connect-src': 1,
          'img-src': 1
        }
      })
      expect(await store.retentionMetrics()).toEqual({
        discardedTotal: 2,
        discardedByAge: 1,
        discardedByMaxEntries: 1
      })
    } finally {
      await store.close()
    }
  })

  it('aggregates CSP rate-limit telemetry in the central Postgres database', async () => {
    const store = new PostgresCspRateLimitTelemetryStore({
      databaseUrl,
      maxEntries: 2
    })
    try {
      await store.clearForTests()

      await store.recordLimited({
        subject: '203.0.113.10',
        at: new Date('2026-05-19T00:00:00.000Z'),
        resetAt: '2026-05-19T00:01:00.000Z'
      })
      await store.recordLimited({
        subject: '203.0.113.11',
        at: new Date('2026-05-19T00:00:01.000Z'),
        resetAt: '2026-05-19T00:01:01.000Z'
      })
      await store.recordLimited({
        subject: '203.0.113.12',
        at: new Date('2026-05-19T00:00:02.000Z'),
        resetAt: '2026-05-19T00:01:02.000Z'
      })

      expect(await store.summary()).toEqual({
        limitedTotal: 2,
        subjectDistribution: {
          uniqueSubjects: 2,
          topSubjects: [
            { rank: 1, count: 1, share: 0.5 },
            { rank: 2, count: 1, share: 0.5 }
          ]
        }
      })
    } finally {
      await store.close()
    }
  })

  it('persists CSP alert transitions in the central Postgres database', async () => {
    const store = new PostgresCspAlertTransitionStore({
      databaseUrl,
      maxEntries: 2
    })
    try {
      await store.clearForTests()

      await store.recordIfChanged({
        active: false,
        reasons: []
      }, new Date('2026-05-20T00:00:00.000Z'))
      await store.recordIfChanged({
        active: true,
        reasons: ['total reports 2 reached threshold 2']
      }, new Date('2026-05-20T00:00:01.000Z'))
      await store.recordIfChanged({
        active: true,
        reasons: ['total reports 2 reached threshold 2']
      }, new Date('2026-05-20T00:00:02.000Z'))

      expect(await store.recent()).toEqual([
        {
          active: true,
          observedAt: '2026-05-20T00:00:01.000Z',
          reasons: ['total reports 2 reached threshold 2']
        },
        {
          active: false,
          observedAt: '2026-05-20T00:00:00.000Z',
          reasons: []
        }
      ])
    } finally {
      await store.close()
    }
  })

  it('persists CSP alert acknowledgements in the central Postgres database', async () => {
    const store = new PostgresCspAlertAcknowledgementStore({
      databaseUrl
    })
    try {
      await store.clearForTests()

      await store.acknowledge({
        fingerprint: 'csp-alert-fingerprint-postgres',
        reason: 'Known deployment',
        acknowledgedBy: 'operator-user',
        acknowledgedAt: new Date('2026-05-20T00:00:00.000Z'),
        snoozeUntil: new Date('2026-05-20T00:15:00.000Z')
      })

      expect(await store.current('csp-alert-fingerprint-postgres', new Date('2026-05-20T00:01:00.000Z'))).toEqual({
        fingerprint: 'csp-alert-fingerprint-postgres',
        status: 'snoozed',
        reason: 'Known deployment',
        acknowledgedBy: 'operator-user',
        acknowledgedAt: '2026-05-20T00:00:00.000Z',
        snoozeUntil: '2026-05-20T00:15:00.000Z'
      })
      expect(await store.current('csp-alert-fingerprint-postgres', new Date('2026-05-20T00:16:00.000Z'))).toMatchObject({
        fingerprint: 'csp-alert-fingerprint-postgres',
        status: 'acknowledged'
      })
    } finally {
      await store.close()
    }
  })

  it('persists CSP alert incident lifecycle events in the central Postgres database', async () => {
    const store = new PostgresCspAlertIncidentStore({
      databaseUrl,
      maxEntries: 2
    })
    try {
      await store.clearForTests()

      await store.append({
        fingerprint: 'csp-alert-fingerprint-postgres',
        eventType: 'acknowledged',
        status: 'acknowledged',
        actor: 'operator-user',
        assignedTo: 'operator-user',
        reason: 'Known deployment',
        occurredAt: '2026-05-20T00:00:00.000Z'
      })
      await store.append({
        fingerprint: 'csp-alert-fingerprint-postgres',
        eventType: 'snoozed',
        status: 'snoozed',
        actor: 'operator-user',
        assignedTo: 'operator-user',
        reason: 'Investigation window',
        occurredAt: '2026-05-20T00:01:00.000Z',
        snoozeUntil: '2026-05-20T00:16:00.000Z'
      })

      expect(await store.recent('csp-alert-fingerprint-postgres')).toEqual([
        {
          fingerprint: 'csp-alert-fingerprint-postgres',
          eventType: 'snoozed',
          status: 'snoozed',
          actor: 'operator-user',
          assignedTo: 'operator-user',
          reason: 'Investigation window',
          occurredAt: '2026-05-20T00:01:00.000Z',
          snoozeUntil: '2026-05-20T00:16:00.000Z'
        },
        {
          fingerprint: 'csp-alert-fingerprint-postgres',
          eventType: 'acknowledged',
          status: 'acknowledged',
          actor: 'operator-user',
          assignedTo: 'operator-user',
          reason: 'Known deployment',
          occurredAt: '2026-05-20T00:00:00.000Z'
        }
      ])
    } finally {
      await store.close()
    }
  })

  it('persists security dashboard operator token hashes and audit rows in the central Postgres database', async () => {
    const store = new PostgresSecurityDashboardOperatorTokenStore({
      databaseUrl
    })
    try {
      await store.clearForTests()

      const issued = await store.issue({
        token: 'sdo_postgres-issued-token',
        actor: 'operator-user',
        issuedAt: new Date('2026-05-21T00:00:00.000Z'),
        expiresAt: new Date('2026-05-21T00:15:00.000Z')
      })

      expect(issued).toEqual({
        token: 'sdo_postgres-issued-token',
        tokenId: expect.stringMatching(/^[a-f0-9]{12}$/),
        expiresAt: '2026-05-21T00:15:00.000Z'
      })
      expect(await store.verify('sdo_postgres-issued-token', new Date('2026-05-21T00:01:00.000Z'))).toMatchObject({
        valid: true,
        actor: 'operator-user'
      })
      expect(JSON.stringify(await store.audit())).not.toContain('sdo_postgres-issued-token')

      await store.revoke({
        token: 'sdo_postgres-issued-token',
        actor: 'operator-user',
        reason: 'test cleanup',
        now: new Date('2026-05-21T00:02:00.000Z')
      })

      expect(await store.verify('sdo_postgres-issued-token', new Date('2026-05-21T00:03:00.000Z'))).toEqual({
        valid: false,
        reason: 'operator token is invalid'
      })
      expect((await store.audit()).map((entry) => entry.action)).toEqual(['revoked', 'issued'])
    } finally {
      await store.close()
    }
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
