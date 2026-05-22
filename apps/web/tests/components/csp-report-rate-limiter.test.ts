import { describe, expect, it } from 'vitest'
import { handleCspReportPayloadAsync } from '../../server/utils/csp-report-handler'
import {
  RedisCspReportRateLimiter,
  type RedisCspReportRateLimitClient
} from '../../server/utils/csp-report-rate-limiter'

describe('CSP report Redis rate limiter', () => {
  it('coordinates report limits across limiter instances sharing Redis state', async () => {
    const redis = new FakeRedisRateLimitClient()
    const firstNode = new RedisCspReportRateLimiter({
      redis,
      maxReports: 2,
      windowMs: 60_000
    })
    const secondNode = new RedisCspReportRateLimiter({
      redis,
      maxReports: 2,
      windowMs: 60_000
    })

    await expect(firstNode.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:00.000Z')
    })).resolves.toMatchObject({
      allowed: true,
      remaining: 1,
      resetAt: '2026-05-19T00:01:00.000Z'
    })
    await expect(secondNode.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:01.000Z')
    })).resolves.toMatchObject({
      allowed: true,
      remaining: 0
    })
    await expect(firstNode.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:02.000Z')
    })).resolves.toMatchObject({
      allowed: false,
      remaining: 0
    })

    expect(new Set(redis.keys).size).toBe(1)
    expect(redis.keys[0]).toContain('csp-report-rate-limit:')
    expect(redis.keys[0]).not.toContain('203.0.113.10')
  })

  it('starts a new Redis counter when the fixed window resets', async () => {
    const redis = new FakeRedisRateLimitClient()
    const limiter = new RedisCspReportRateLimiter({
      redis,
      maxReports: 1,
      windowMs: 60_000
    })

    await expect(limiter.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:59.000Z')
    })).resolves.toMatchObject({ allowed: true, remaining: 0 })
    await expect(limiter.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:01:00.000Z')
    })).resolves.toMatchObject({
      allowed: true,
      remaining: 0,
      resetAt: '2026-05-19T00:02:00.000Z'
    })

    expect(new Set(redis.keys).size).toBe(2)
  })

  it('fails closed when Redis cannot consume the counter', async () => {
    const limiter = new RedisCspReportRateLimiter({
      redis: {
        incrementAndExpire: async () => {
          throw new Error('redis unavailable')
        }
      },
      maxReports: 10,
      windowMs: 60_000
    })

    await expect(limiter.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:00.000Z')
    })).resolves.toEqual({
      allowed: false,
      limit: 10,
      remaining: 0,
      resetAt: '2026-05-19T00:01:00.000Z'
    })

    expect(limiter.lifecycleMetrics()).toEqual({
      backend: 'redis',
      failClosedDecisions: 1
    })
  })

  it('surfaces Redis client lifecycle metrics without exposing connection details', async () => {
    const redis = new FakeRedisRateLimitClient()
    const limiter = new RedisCspReportRateLimiter({
      redis,
      maxReports: 1,
      windowMs: 60_000
    })

    await limiter.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:00.000Z')
    })
    await limiter.close()

    expect(limiter.lifecycleMetrics()).toEqual({
      backend: 'redis',
      failClosedDecisions: 0,
      redis: {
        connectAttempts: 1,
        connectSuccesses: 1,
        connectFailures: 0,
        errorEvents: 0,
        reconnectEvents: 0,
        closeCalls: 1,
        lastConnectedAt: '2026-05-19T00:00:00.000Z',
        lastClosedAt: '2026-05-19T00:00:01.000Z'
      }
    })
    expect(JSON.stringify(limiter.lifecycleMetrics())).not.toContain('redis://')
    expect(JSON.stringify(limiter.lifecycleMetrics())).not.toContain('dev_password')
  })

  it('closes the underlying Redis client when the limiter is closed', async () => {
    const redis = new FakeRedisRateLimitClient()
    const limiter = new RedisCspReportRateLimiter({
      redis,
      maxReports: 1,
      windowMs: 60_000
    })

    await limiter.close()

    expect(redis.closeCalls).toBe(1)
  })

  it('lets the async CSP report handler await Redis rate-limit decisions before parsing payloads', async () => {
    const result = await handleCspReportPayloadAsync({
      body: '{"not":"parsed when limited"}',
      contentType: 'application/csp-report',
      requestId: 'req-csp-redis-limited',
      userAgent: 'Playwright',
      rateLimitSubject: '203.0.113.10'
    }, {
      rateLimiter: new RedisCspReportRateLimiter({
        redis: new FakeRedisRateLimitClient(2),
        maxReports: 1,
        windowMs: 60_000
      }),
      now: () => new Date('2026-05-19T00:00:00.000Z')
    })

    expect(result).toMatchObject({
      accepted: false,
      statusCode: 204,
      reason: 'rate limited'
    })
  })
})

class FakeRedisRateLimitClient implements RedisCspReportRateLimitClient {
  readonly counts = new Map<string, number>()
  readonly keys: string[] = []
  closeCalls = 0
  connected = false

  constructor(private readonly firstCount = 0) {
  }

  async incrementAndExpire(key: string): Promise<number> {
    this.connected = true
    this.keys.push(key)
    const count = (this.counts.get(key) ?? this.firstCount) + 1
    this.counts.set(key, count)
    return count
  }

  async close(): Promise<void> {
    this.closeCalls += 1
  }

  lifecycleMetrics() {
    return {
      connectAttempts: this.connected ? 1 : 0,
      connectSuccesses: this.connected ? 1 : 0,
      connectFailures: 0,
      errorEvents: 0,
      reconnectEvents: 0,
      closeCalls: this.closeCalls,
      ...(this.connected ? { lastConnectedAt: '2026-05-19T00:00:00.000Z' } : {}),
      ...(this.closeCalls > 0 ? { lastClosedAt: '2026-05-19T00:00:01.000Z' } : {})
    }
  }
}
