import { createHash } from 'node:crypto'
import postgres from 'postgres'

type MaybePromise<T> = T | Promise<T>

export interface CspRateLimitTelemetryInput {
  subject: string
  at: Date
  resetAt: string
}

export interface CspRateLimitTelemetrySummary {
  limitedTotal: number
  subjectDistribution: CspRateLimitSubjectDistribution
}

export interface CspRateLimitSubjectDistributionBucket {
  rank: number
  count: number
  share: number
}

export interface CspRateLimitSubjectDistribution {
  uniqueSubjects: number
  topSubjects: CspRateLimitSubjectDistributionBucket[]
}

export interface CspRateLimitTelemetryStore {
  recordLimited(input: CspRateLimitTelemetryInput): MaybePromise<void>
  summary(): MaybePromise<CspRateLimitTelemetrySummary>
  close?(): MaybePromise<void>
}

interface StoredCspRateLimitTelemetry {
  subjectHash: string
  receivedAt: string
  resetAt: string
}

export interface InMemoryCspRateLimitTelemetryStoreOptions {
  maxEntries?: number
}

export class InMemoryCspRateLimitTelemetryStore implements CspRateLimitTelemetryStore {
  private readonly maxEntries: number
  private readonly entries: StoredCspRateLimitTelemetry[] = []

  constructor(options: InMemoryCspRateLimitTelemetryStoreOptions = {}) {
    this.maxEntries = Math.max(1, options.maxEntries ?? 1_000)
  }

  recordLimited(input: CspRateLimitTelemetryInput): void {
    this.entries.unshift({
      subjectHash: hashSubject(input.subject),
      receivedAt: input.at.toISOString(),
      resetAt: input.resetAt
    })
    if (this.entries.length > this.maxEntries) {
      this.entries.length = this.maxEntries
    }
  }

  summary(): CspRateLimitTelemetrySummary {
    const counts = new Map<string, number>()
    for (const entry of this.entries) {
      counts.set(entry.subjectHash, (counts.get(entry.subjectHash) ?? 0) + 1)
    }
    return {
      limitedTotal: this.entries.length,
      subjectDistribution: subjectDistributionFromCounts([...counts.values()])
    }
  }
}

export interface PostgresCspRateLimitTelemetryStoreOptions {
  databaseUrl: string
  maxEntries?: number
}

export class PostgresCspRateLimitTelemetryStore implements CspRateLimitTelemetryStore {
  private readonly sql: ReturnType<typeof postgres>
  private readonly maxEntries: number
  private initialized?: Promise<void>

  constructor(options: PostgresCspRateLimitTelemetryStoreOptions) {
    if (!options.databaseUrl.trim()) {
      throw new Error('databaseUrl is required')
    }
    this.maxEntries = telemetryMaxEntries(options.maxEntries)
    this.sql = postgres(options.databaseUrl, { max: 3 })
  }

  async recordLimited(input: CspRateLimitTelemetryInput): Promise<void> {
    await this.ensureSchema()
    await this.sql`
      INSERT INTO csp_rate_limit_telemetry (
        received_at,
        subject_hash,
        reset_at
      ) VALUES (
        ${input.at.toISOString()},
        ${hashSubject(input.subject)},
        ${input.resetAt}
      )
    `
    await this.prune()
  }

  async summary(): Promise<CspRateLimitTelemetrySummary> {
    await this.ensureSchema()
    const rows = await this.sql<Array<{ total: string }>>`
      SELECT COUNT(*) AS total
      FROM csp_rate_limit_telemetry
    `
    const subjectRows = await this.sql<Array<{ count: string }>>`
      SELECT COUNT(*) AS count
      FROM csp_rate_limit_telemetry
      GROUP BY subject_hash
      ORDER BY COUNT(*) DESC, subject_hash ASC
      LIMIT 5
    `
    const uniqueRows = await this.sql<Array<{ total: string }>>`
      SELECT COUNT(DISTINCT subject_hash) AS total
      FROM csp_rate_limit_telemetry
    `
    return {
      limitedTotal: Number(rows[0]?.total ?? 0),
      subjectDistribution: subjectDistributionFromCounts(
        subjectRows.map((row) => Number(row.count)),
        Number(uniqueRows[0]?.total ?? 0),
        Number(rows[0]?.total ?? 0)
      )
    }
  }

  async clearForTests(): Promise<void> {
    await this.ensureSchema()
    await this.sql`TRUNCATE TABLE csp_rate_limit_telemetry`
  }

  async close(): Promise<void> {
    await this.sql.end()
  }

  private ensureSchema(): Promise<void> {
    this.initialized ??= (async () => {
      await this.sql`
        CREATE TABLE IF NOT EXISTS csp_rate_limit_telemetry (
          id BIGSERIAL PRIMARY KEY,
          received_at TIMESTAMPTZ NOT NULL,
          subject_hash TEXT NOT NULL,
          reset_at TIMESTAMPTZ NOT NULL
        )
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_csp_rate_limit_telemetry_received_at
        ON csp_rate_limit_telemetry(received_at DESC, id DESC)
      `
    })()
    return this.initialized
  }

  private async prune(): Promise<void> {
    await this.sql`
      DELETE FROM csp_rate_limit_telemetry
      WHERE id NOT IN (
        SELECT id
        FROM csp_rate_limit_telemetry
        ORDER BY received_at DESC, id DESC
        LIMIT ${this.maxEntries}
      )
    `
  }
}

const hashSubject = (subject: string): string =>
  createHash('sha256')
    .update(subject.trim() || 'unknown')
    .digest('hex')

const subjectDistributionFromCounts = (
  counts: number[],
  uniqueSubjects = counts.length,
  limitedTotalOverride?: number
): CspRateLimitSubjectDistribution => {
  const sortedCounts = counts
    .filter((count) => Number.isFinite(count) && count > 0)
    .sort((left, right) => right - left)
  const limitedTotal = Number.isFinite(limitedTotalOverride)
    ? Math.max(0, Math.trunc(limitedTotalOverride ?? 0))
    : sortedCounts.reduce((total, count) => total + count, 0)
  return {
    uniqueSubjects: Math.max(0, Math.trunc(uniqueSubjects)),
    topSubjects: sortedCounts.slice(0, 5).map((count, index) => ({
      rank: index + 1,
      count,
      share: limitedTotal > 0 ? Number((count / limitedTotal).toFixed(4)) : 0
    }))
  }
}

export interface DefaultCspRateLimitTelemetryStoreOptions {
  databaseUrl?: string
  maxEntries?: number
}

export const createDefaultCspRateLimitTelemetryStore = (
  options: DefaultCspRateLimitTelemetryStoreOptions = {}
): CspRateLimitTelemetryStore => {
  const databaseUrl = options.databaseUrl?.trim()
    || process.env.NUXT_CSP_RATE_LIMIT_TELEMETRY_POSTGRES_URL?.trim()
    || process.env.NUXT_CSP_TELEMETRY_POSTGRES_URL?.trim()
  const maxEntries = telemetryMaxEntries(
    options.maxEntries ?? parsePositiveInteger(process.env.NUXT_CSP_RATE_LIMIT_TELEMETRY_MAX_ENTRIES)
  )
  if (databaseUrl) {
    return new PostgresCspRateLimitTelemetryStore({ databaseUrl, maxEntries })
  }
  return new InMemoryCspRateLimitTelemetryStore({ maxEntries })
}

const telemetryMaxEntries = (value: number | undefined): number => {
  if (!Number.isFinite(value)) {
    return 1_000
  }
  return Math.max(1, Math.trunc(value ?? 1_000))
}

const parsePositiveInteger = (value: string | undefined): number | undefined => {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

export const defaultCspRateLimitTelemetryStore = createDefaultCspRateLimitTelemetryStore()
