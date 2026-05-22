import { createHash } from 'node:crypto'
import { createClient } from 'redis'

export interface CspReportRateLimitInput {
  subject: string
  at: Date
}

export interface CspReportRateLimitDecision {
  allowed: boolean
  limit: number
  remaining: number
  resetAt: string
}

export interface RedisCspReportRateLimiterLifecycleMetrics {
  connectAttempts: number
  connectSuccesses: number
  connectFailures: number
  errorEvents: number
  reconnectEvents: number
  closeCalls: number
  lastConnectedAt?: string
  lastClosedAt?: string
  lastErrorAt?: string
}

export interface CspReportRateLimiterLifecycleMetrics {
  backend: 'memory' | 'redis'
  failClosedDecisions: number
  redis?: RedisCspReportRateLimiterLifecycleMetrics
}

export interface CspReportRateLimiter {
  consume(input: CspReportRateLimitInput): CspReportRateLimitDecision | Promise<CspReportRateLimitDecision>
  close?(): void | Promise<void>
  lifecycleMetrics?(): CspReportRateLimiterLifecycleMetrics
}

export interface InMemoryCspReportRateLimiterOptions {
  maxReports?: number
  windowMs?: number
  maxSubjects?: number
}

interface RateLimitWindow {
  count: number
  windowStartMs: number
  lastSeenMs: number
}

export class InMemoryCspReportRateLimiter implements CspReportRateLimiter {
  private readonly maxReports: number
  private readonly windowMs: number
  private readonly maxSubjects: number
  private readonly windows = new Map<string, RateLimitWindow>()

  constructor(options: InMemoryCspReportRateLimiterOptions = {}) {
    this.maxReports = Math.max(1, options.maxReports ?? 60)
    this.windowMs = Math.max(1_000, options.windowMs ?? 60_000)
    this.maxSubjects = Math.max(1, options.maxSubjects ?? 5_000)
  }

  consume(input: CspReportRateLimitInput): CspReportRateLimitDecision {
    const subject = normalizedSubject(input.subject)
    const nowMs = input.at.getTime()
    const existing = this.windows.get(subject)
    const current = existing && nowMs - existing.windowStartMs < this.windowMs
      ? existing
      : {
          count: 0,
          windowStartMs: nowMs,
          lastSeenMs: nowMs
        }

    current.count += 1
    current.lastSeenMs = nowMs
    this.windows.set(subject, current)
    this.pruneIfNeeded()

    const resetAt = new Date(current.windowStartMs + this.windowMs).toISOString()
    return {
      allowed: current.count <= this.maxReports,
      limit: this.maxReports,
      remaining: Math.max(this.maxReports - current.count, 0),
      resetAt
    }
  }

  lifecycleMetrics(): CspReportRateLimiterLifecycleMetrics {
    return {
      backend: 'memory',
      failClosedDecisions: 0
    }
  }

  private pruneIfNeeded(): void {
    if (this.windows.size <= this.maxSubjects) {
      return
    }

    const subjectsByAge = [...this.windows.entries()]
      .sort(([, left], [, right]) => left.lastSeenMs - right.lastSeenMs)
      .slice(0, this.windows.size - this.maxSubjects)

    for (const [subject] of subjectsByAge) {
      this.windows.delete(subject)
    }
  }
}

export interface RedisCspReportRateLimitClient {
  incrementAndExpire(key: string, ttlMs: number): Promise<number>
  close?(): Promise<void>
  lifecycleMetrics?(): RedisCspReportRateLimiterLifecycleMetrics
}

export interface RedisCspReportRateLimiterOptions {
  redis: RedisCspReportRateLimitClient
  maxReports?: number
  windowMs?: number
  keyPrefix?: string
}

export class RedisCspReportRateLimiter implements CspReportRateLimiter {
  private readonly redis: RedisCspReportRateLimitClient
  private readonly maxReports: number
  private readonly windowMs: number
  private readonly keyPrefix: string
  private failClosedDecisions = 0

  constructor(options: RedisCspReportRateLimiterOptions) {
    this.redis = options.redis
    this.maxReports = Math.max(1, options.maxReports ?? 60)
    this.windowMs = Math.max(1_000, options.windowMs ?? 60_000)
    this.keyPrefix = (options.keyPrefix ?? 'csp-report-rate-limit').trim() || 'csp-report-rate-limit'
  }

  async consume(input: CspReportRateLimitInput): Promise<CspReportRateLimitDecision> {
    const nowMs = input.at.getTime()
    const windowStartMs = Math.floor(nowMs / this.windowMs) * this.windowMs
    const resetAt = new Date(windowStartMs + this.windowMs).toISOString()
    const ttlMs = Math.max(windowStartMs + this.windowMs - nowMs, 1)
    try {
      const count = await this.redis.incrementAndExpire(this.keyFor(input.subject, windowStartMs), ttlMs)
      return {
        allowed: count <= this.maxReports,
        limit: this.maxReports,
        remaining: Math.max(this.maxReports - Math.min(count, Number.MAX_SAFE_INTEGER), 0),
        resetAt
      }
    } catch {
      this.failClosedDecisions += 1
      return {
        allowed: false,
        limit: this.maxReports,
        remaining: 0,
        resetAt
      }
    }
  }

  async close(): Promise<void> {
    await this.redis.close?.()
  }

  lifecycleMetrics(): CspReportRateLimiterLifecycleMetrics {
    const redis = this.redis.lifecycleMetrics?.()
    return {
      backend: 'redis',
      failClosedDecisions: this.failClosedDecisions,
      ...(redis ? { redis } : {})
    }
  }

  private keyFor(subject: string, windowStartMs: number): string {
    const subjectHash = createHash('sha256')
      .update(normalizedSubject(subject))
      .digest('hex')
      .slice(0, 32)
    return `${this.keyPrefix}:${subjectHash}:${windowStartMs}`
  }
}

export interface NodeRedisCspReportRateLimitClientOptions {
  url: string
}

export class NodeRedisCspReportRateLimitClient implements RedisCspReportRateLimitClient {
  private readonly url: string
  private clientPromise?: Promise<ReturnType<typeof createClient>>
  private metrics: RedisCspReportRateLimiterLifecycleMetrics = {
    connectAttempts: 0,
    connectSuccesses: 0,
    connectFailures: 0,
    errorEvents: 0,
    reconnectEvents: 0,
    closeCalls: 0
  }

  constructor(options: NodeRedisCspReportRateLimitClientOptions) {
    this.url = options.url
  }

  async incrementAndExpire(key: string, ttlMs: number): Promise<number> {
    const client = await this.client()
    const count = await client.eval(INCREMENT_AND_EXPIRE_SCRIPT, {
      keys: [key],
      arguments: [String(ttlMs)]
    })
    return Number(count)
  }

  async close(): Promise<void> {
    if (!this.clientPromise) {
      return
    }
    const client = await this.clientPromise
    if (client.isOpen) {
      await client.quit()
    }
    this.metrics.closeCalls += 1
    this.metrics.lastClosedAt = new Date().toISOString()
    this.clientPromise = undefined
  }

  lifecycleMetrics(): RedisCspReportRateLimiterLifecycleMetrics {
    return { ...this.metrics }
  }

  private client(): Promise<ReturnType<typeof createClient>> {
    if (!this.clientPromise) {
      const client = createClient({ url: this.url })
      client.on('error', () => {
        this.metrics.errorEvents += 1
        this.metrics.lastErrorAt = new Date().toISOString()
        console.warn('csp_report_rate_limit_redis_error')
      })
      client.on('reconnecting', () => {
        this.metrics.reconnectEvents += 1
      })
      this.metrics.connectAttempts += 1
      this.clientPromise = client.connect()
        .then(() => {
          this.metrics.connectSuccesses += 1
          this.metrics.lastConnectedAt = new Date().toISOString()
          return client
        })
        .catch((error: unknown) => {
          this.metrics.connectFailures += 1
          this.metrics.lastErrorAt = new Date().toISOString()
          this.clientPromise = undefined
          throw error
        })
    }
    return this.clientPromise
  }
}

export interface DefaultCspReportRateLimiterOptions {
  redisUrl?: string
  maxReports?: number
  windowMs?: number
}

export const createDefaultCspReportRateLimiter = (
  options: DefaultCspReportRateLimiterOptions = {}
): CspReportRateLimiter => {
  const maxReports = positiveInteger(
    options.maxReports,
    process.env.NUXT_CSP_REPORT_RATE_LIMIT_MAX_REPORTS,
    60
  )
  const windowMs = positiveInteger(
    options.windowMs,
    process.env.NUXT_CSP_REPORT_RATE_LIMIT_WINDOW_MS,
    60_000
  )
  const redisUrl = options.redisUrl?.trim() || process.env.NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL?.trim()
  if (redisUrl) {
    return new RedisCspReportRateLimiter({
      redis: new NodeRedisCspReportRateLimitClient({ url: redisUrl }),
      maxReports,
      windowMs
    })
  }
  return new InMemoryCspReportRateLimiter({ maxReports, windowMs })
}

const normalizedSubject = (subject: string): string => {
  const trimmed = subject.trim()
  return trimmed.length > 0 ? trimmed.slice(0, 128) : 'unknown'
}

const positiveInteger = (
  optionValue: number | undefined,
  envValue: string | undefined,
  fallback: number
): number => {
  if (Number.isFinite(optionValue) && optionValue !== undefined && optionValue > 0) {
    return Math.trunc(optionValue)
  }
  const parsed = Number.parseInt(envValue ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback
}

const INCREMENT_AND_EXPIRE_SCRIPT = `
local count = redis.call('INCR', KEYS[1])
if count == 1 then
  redis.call('PEXPIRE', KEYS[1], ARGV[1])
end
return count
`

export const defaultCspReportRateLimiter = createDefaultCspReportRateLimiter()
