import { execFileSync } from 'node:child_process'
import { setTimeout as sleep } from 'node:timers/promises'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import {
  NodeRedisCspReportRateLimitClient,
  RedisCspReportRateLimiter
} from '../../server/utils/csp-report-rate-limiter'

const dockerTestsEnabled = process.env.NUXT_RUN_DOCKER_TESTS === 'true'

describe.skipIf(!dockerTestsEnabled)('CSP report Redis limiter integration', () => {
  const containerName = `discord-csp-redis-${process.pid}-${Date.now()}`
  const clients: NodeRedisCspReportRateLimitClient[] = []
  let redisUrl = ''

  beforeAll(async () => {
    docker([
      'run',
      '--rm',
      '-d',
      '--name',
      containerName,
      '-p',
      '127.0.0.1::6379',
      'redis:7-alpine'
    ])
    await waitForRedis(containerName)
    const port = docker(['port', containerName, '6379/tcp'])
      .split('\n')[0]
      .trim()
      .split(':')
      .at(-1)
    redisUrl = `redis://127.0.0.1:${port}`
  }, 120_000)

  afterAll(async () => {
    await Promise.all(clients.map((client) => client.close()))
    docker(['stop', containerName], { allowFailure: true })
  })

  it('coordinates CSP report limits through a real Redis counter', async () => {
    const firstClient = new NodeRedisCspReportRateLimitClient({ url: redisUrl })
    const secondClient = new NodeRedisCspReportRateLimitClient({ url: redisUrl })
    clients.push(firstClient, secondClient)
    const firstNode = new RedisCspReportRateLimiter({
      redis: firstClient,
      maxReports: 2,
      windowMs: 60_000
    })
    const secondNode = new RedisCspReportRateLimiter({
      redis: secondClient,
      maxReports: 2,
      windowMs: 60_000
    })

    await expect(firstNode.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:00.000Z')
    })).resolves.toMatchObject({ allowed: true, remaining: 1 })
    await expect(secondNode.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:01.000Z')
    })).resolves.toMatchObject({ allowed: true, remaining: 0 })
    await expect(firstNode.consume({
      subject: '203.0.113.10',
      at: new Date('2026-05-19T00:00:02.000Z')
    })).resolves.toMatchObject({ allowed: false, remaining: 0 })
  })
})

const docker = (args: string[], options: { allowFailure?: boolean } = {}): string => {
  try {
    return execFileSync('docker', args, {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe']
    }).trim()
  } catch (error) {
    if (options.allowFailure) {
      return ''
    }
    throw error
  }
}

const waitForRedis = async (containerName: string): Promise<void> => {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    try {
      if (docker(['exec', containerName, 'redis-cli', 'ping']) === 'PONG') {
        return
      }
    } catch {
      await sleep(1_000)
    }
  }
  throw new Error('Redis container did not become ready')
}
