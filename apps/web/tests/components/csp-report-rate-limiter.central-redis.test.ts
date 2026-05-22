import { describe, expect, it } from 'vitest'
import {
  NodeRedisCspReportRateLimitClient,
  RedisCspReportRateLimiter
} from '../../server/utils/csp-report-rate-limiter'

const centralRedisEnabled = process.env.NUXT_RUN_CENTRAL_REDIS_SMOKE === 'true'

describe.skipIf(!centralRedisEnabled)('CSP report central Redis limiter smoke', () => {
  it('coordinates CSP report limits through the central Redis endpoint', async () => {
    const redisUrl = process.env.NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL
    expect(redisUrl).toBeTruthy()

    const firstClient = new NodeRedisCspReportRateLimitClient({ url: redisUrl! })
    const secondClient = new NodeRedisCspReportRateLimitClient({ url: redisUrl! })
    const keyPrefix = `central-smoke-${process.pid}-${Date.now()}`
    try {
      const firstNode = new RedisCspReportRateLimiter({
        redis: firstClient,
        maxReports: 2,
        windowMs: 60_000,
        keyPrefix
      })
      const secondNode = new RedisCspReportRateLimiter({
        redis: secondClient,
        maxReports: 2,
        windowMs: 60_000,
        keyPrefix
      })

      await expect(firstNode.consume({
        subject: '203.0.113.10',
        at: new Date('2026-05-20T00:00:00.000Z')
      })).resolves.toMatchObject({ allowed: true, remaining: 1 })
      await expect(secondNode.consume({
        subject: '203.0.113.10',
        at: new Date('2026-05-20T00:00:01.000Z')
      })).resolves.toMatchObject({ allowed: true, remaining: 0 })
      await expect(firstNode.consume({
        subject: '203.0.113.10',
        at: new Date('2026-05-20T00:00:02.000Z')
      })).resolves.toMatchObject({ allowed: false, remaining: 0 })
    } finally {
      await Promise.all([firstClient.close(), secondClient.close()])
    }
  })
})
