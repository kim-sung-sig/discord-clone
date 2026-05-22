import postgres from 'postgres'
import type { NormalizedCspReport } from './security-headers'

type MaybePromise<T> = T | Promise<T>

export interface StoredCspTelemetry {
  report: NormalizedCspReport
  receivedAt: string
}

export interface CspTelemetrySummary {
  total: number
  byEffectiveDirective: Record<string, number>
}

export interface CspTelemetryRetentionMetrics {
  discardedTotal: number
  discardedByAge: number
  discardedByMaxEntries: number
}

export type CspTelemetryStorageBackend = 'memory' | 'postgres' | 'unknown'

export interface CspTelemetryStorageHealth {
  backend: CspTelemetryStorageBackend
  ok: boolean
  writeFailures: number
  lastWriteFailureAt?: string
  lastError?: string
}

export interface CspTelemetryStore {
  record(report: NormalizedCspReport, receivedAt: Date): MaybePromise<void>
  recent(limit?: number): MaybePromise<StoredCspTelemetry[]>
  summary(): MaybePromise<CspTelemetrySummary>
  retentionMetrics(): MaybePromise<CspTelemetryRetentionMetrics>
  health?(): MaybePromise<CspTelemetryStorageHealth>
  close?(): MaybePromise<void>
}

export interface CspTelemetryRetentionPolicy {
  maxEntries?: number
  maxAgeMs?: number
}

export interface InMemoryCspTelemetryStoreOptions extends CspTelemetryRetentionPolicy {
}

export class InMemoryCspTelemetryStore implements CspTelemetryStore {
  private readonly maxEntries: number
  private readonly maxAgeMs?: number
  private readonly entries: StoredCspTelemetry[] = []
  private readonly metrics: CspTelemetryRetentionMetrics = {
    discardedTotal: 0,
    discardedByAge: 0,
    discardedByMaxEntries: 0
  }

  constructor(options: InMemoryCspTelemetryStoreOptions = {}) {
    this.maxEntries = retentionMaxEntries(options.maxEntries)
    this.maxAgeMs = retentionMaxAgeMs(options.maxAgeMs)
  }

  record(report: NormalizedCspReport, receivedAt: Date): void {
    this.entries.unshift({
      report: { ...report },
      receivedAt: receivedAt.toISOString()
    })
    if (this.entries.length > this.maxEntries) {
      this.recordRetentionDiscard('discardedByMaxEntries', this.entries.length - this.maxEntries)
      this.entries.length = this.maxEntries
    }
    this.pruneByAge(receivedAt)
  }

  recent(limit = this.maxEntries): StoredCspTelemetry[] {
    return this.entries.slice(0, Math.max(0, limit)).map((entry) => ({
      report: { ...entry.report },
      receivedAt: entry.receivedAt
    }))
  }

  summary(): CspTelemetrySummary {
    const byEffectiveDirective: Record<string, number> = {}
    for (const entry of this.entries) {
      byEffectiveDirective[entry.report.effectiveDirective] =
        (byEffectiveDirective[entry.report.effectiveDirective] ?? 0) + 1
    }
    return {
      total: this.entries.length,
      byEffectiveDirective
    }
  }

  retentionMetrics(): CspTelemetryRetentionMetrics {
    return { ...this.metrics }
  }

  health(): CspTelemetryStorageHealth {
    return {
      backend: 'memory',
      ok: true,
      writeFailures: 0
    }
  }

  private pruneByAge(now: Date): void {
    if (!this.maxAgeMs) {
      return
    }
    const cutoffTime = now.getTime() - this.maxAgeMs
    for (let index = this.entries.length - 1; index >= 0; index -= 1) {
      if (new Date(this.entries[index].receivedAt).getTime() < cutoffTime) {
        this.entries.splice(index, 1)
        this.recordRetentionDiscard('discardedByAge', 1)
      }
    }
  }

  private recordRetentionDiscard(metric: 'discardedByAge' | 'discardedByMaxEntries', count: number): void {
    if (count <= 0) {
      return
    }
    this.metrics[metric] += count
    this.metrics.discardedTotal += count
  }
}

export interface PostgresCspTelemetryStoreOptions extends CspTelemetryRetentionPolicy {
  databaseUrl: string
}

export class PostgresCspTelemetryStore implements CspTelemetryStore {
  private readonly sql: ReturnType<typeof postgres>
  private readonly maxEntries: number
  private readonly maxAgeMs?: number
  private initialized?: Promise<void>
  private writeFailures = 0
  private lastWriteFailureAt?: string
  private lastWriteFailureMessage?: string

  constructor(options: PostgresCspTelemetryStoreOptions) {
    if (!options.databaseUrl.trim()) {
      throw new Error('databaseUrl is required')
    }
    this.maxEntries = retentionMaxEntries(options.maxEntries)
    this.maxAgeMs = retentionMaxAgeMs(options.maxAgeMs)
    this.sql = postgres(options.databaseUrl, { max: 5 })
  }

  async record(report: NormalizedCspReport, receivedAt: Date): Promise<void> {
    try {
      await this.ensureSchema()
      await this.sql`
        INSERT INTO csp_telemetry (
          received_at,
          request_id,
          document_uri_origin,
          blocked_uri_origin,
          violated_directive,
          effective_directive,
          disposition,
          user_agent_hash
        ) VALUES (
          ${receivedAt.toISOString()},
          ${report.requestId},
          ${report.documentUriOrigin},
          ${report.blockedUriOrigin},
          ${report.violatedDirective},
          ${report.effectiveDirective},
          ${report.disposition},
          ${report.userAgentHash}
        )
      `
      await this.prune(receivedAt)
    } catch (error) {
      this.recordWriteFailure(error)
      throw error
    }
  }

  async recent(limit = 1_000): Promise<StoredCspTelemetry[]> {
    await this.ensureSchema()
    const boundedLimit = Math.min(Math.max(Math.trunc(limit), 0), 1_000)
    const rows = await this.sql<PostgresCspTelemetryRow[]>`
      SELECT
        received_at,
        request_id,
        document_uri_origin,
        blocked_uri_origin,
        violated_directive,
        effective_directive,
        disposition,
        user_agent_hash
      FROM csp_telemetry
      ORDER BY received_at DESC, id DESC
      LIMIT ${boundedLimit}
    `

    return rows.map(rowToStoredTelemetry)
  }

  async summary(): Promise<CspTelemetrySummary> {
    await this.ensureSchema()
    const totalRows = await this.sql<Array<{ total: string }>>`SELECT COUNT(*) AS total FROM csp_telemetry`
    const directiveRows = await this.sql<Array<{ effective_directive: string, count: string }>>`
      SELECT effective_directive, COUNT(*) AS count
      FROM csp_telemetry
      GROUP BY effective_directive
    `
    const byEffectiveDirective: Record<string, number> = {}
    for (const row of directiveRows) {
      byEffectiveDirective[row.effective_directive] = Number(row.count)
    }
    return {
      total: Number(totalRows[0]?.total ?? 0),
      byEffectiveDirective
    }
  }

  async retentionMetrics(): Promise<CspTelemetryRetentionMetrics> {
    await this.ensureSchema()
    const discardedByAge = await this.retentionMetricValue('discardedByAge')
    const discardedByMaxEntries = await this.retentionMetricValue('discardedByMaxEntries')
    return {
      discardedTotal: discardedByAge + discardedByMaxEntries,
      discardedByAge,
      discardedByMaxEntries
    }
  }

  async clearForTests(): Promise<void> {
    await this.ensureSchema()
    await this.sql`TRUNCATE TABLE csp_telemetry, csp_telemetry_retention_metrics`
  }

  async close(): Promise<void> {
    await this.sql.end()
  }

  async health(): Promise<CspTelemetryStorageHealth> {
    const base = this.storageHealthBase()
    try {
      await this.ensureSchema()
      await this.sql`SELECT 1`
      return {
        ...base,
        ok: true
      }
    } catch (error) {
      return {
        ...base,
        ok: false,
        lastError: sanitizeStorageError(error)
      }
    }
  }

  private ensureSchema(): Promise<void> {
    this.initialized ??= (async () => {
      await this.sql`
        CREATE TABLE IF NOT EXISTS csp_telemetry (
          id BIGSERIAL PRIMARY KEY,
          received_at TIMESTAMPTZ NOT NULL,
          request_id TEXT NOT NULL,
          document_uri_origin TEXT NOT NULL,
          blocked_uri_origin TEXT NOT NULL,
          violated_directive TEXT NOT NULL,
          effective_directive TEXT NOT NULL,
          disposition TEXT NOT NULL,
          user_agent_hash TEXT NOT NULL
        )
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_csp_telemetry_received_at
        ON csp_telemetry(received_at DESC, id DESC)
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_csp_telemetry_effective_directive
        ON csp_telemetry(effective_directive)
      `
      await this.sql`
        CREATE TABLE IF NOT EXISTS csp_telemetry_retention_metrics (
          metric TEXT PRIMARY KEY,
          value BIGINT NOT NULL DEFAULT 0
        )
      `
    })()
    return this.initialized
  }

  private async prune(now: Date): Promise<void> {
    if (this.maxAgeMs) {
      const cutoff = new Date(now.getTime() - this.maxAgeMs).toISOString()
      const result = await this.sql`DELETE FROM csp_telemetry WHERE received_at < ${cutoff}`
      await this.incrementRetentionMetric('discardedByAge', result.count)
    }
    const result = await this.sql`
      DELETE FROM csp_telemetry
      WHERE id NOT IN (
        SELECT id
        FROM csp_telemetry
        ORDER BY received_at DESC, id DESC
        LIMIT ${this.maxEntries}
      )
    `
    await this.incrementRetentionMetric('discardedByMaxEntries', result.count)
  }

  private async incrementRetentionMetric(
    metric: 'discardedByAge' | 'discardedByMaxEntries',
    count: number
  ): Promise<void> {
    if (count <= 0) {
      return
    }
    await this.sql`
      INSERT INTO csp_telemetry_retention_metrics (metric, value)
      VALUES (${metric}, ${count})
      ON CONFLICT(metric) DO UPDATE SET value = csp_telemetry_retention_metrics.value + excluded.value
    `
  }

  private async retentionMetricValue(metric: 'discardedByAge' | 'discardedByMaxEntries'): Promise<number> {
    const rows = await this.sql<Array<{ value: string }>>`
      SELECT value
      FROM csp_telemetry_retention_metrics
      WHERE metric = ${metric}
    `
    return Number(rows[0]?.value ?? 0)
  }

  private recordWriteFailure(error: unknown): void {
    this.writeFailures += 1
    this.lastWriteFailureAt = new Date().toISOString()
    this.lastWriteFailureMessage = sanitizeStorageError(error)
  }

  private storageHealthBase(): CspTelemetryStorageHealth {
    return {
      backend: 'postgres',
      ok: true,
      writeFailures: this.writeFailures,
      ...(this.lastWriteFailureAt ? { lastWriteFailureAt: this.lastWriteFailureAt } : {}),
      ...(this.lastWriteFailureMessage ? { lastError: this.lastWriteFailureMessage } : {})
    }
  }
}

export interface DefaultCspTelemetryStoreOptions extends CspTelemetryRetentionPolicy {
  databaseUrl?: string
}

export const createDefaultCspTelemetryStore = (options: DefaultCspTelemetryStoreOptions = {}): CspTelemetryStore => {
  const retention = retentionPolicyFromOptions(options)
  const databaseUrl = options.databaseUrl?.trim() || process.env.NUXT_CSP_TELEMETRY_POSTGRES_URL?.trim()
  if (databaseUrl) {
    return new PostgresCspTelemetryStore({ databaseUrl, ...retention })
  }
  return new InMemoryCspTelemetryStore(retention)
}

const retentionMaxEntries = (value: number | undefined): number => {
  if (!Number.isFinite(value)) {
    return 1_000
  }
  return Math.max(1, Math.trunc(value ?? 1_000))
}

const retentionMaxAgeMs = (value: number | undefined): number | undefined => {
  if (!Number.isFinite(value) || value === undefined || value <= 0) {
    return undefined
  }
  return Math.trunc(value)
}

const retentionPolicyFromOptions = (
  options: DefaultCspTelemetryStoreOptions
): CspTelemetryRetentionPolicy => ({
  maxEntries: retentionMaxEntries(
    options.maxEntries ?? parsePositiveInteger(process.env.NUXT_CSP_TELEMETRY_RETENTION_MAX_ENTRIES)
  ),
  maxAgeMs: retentionMaxAgeMs(
    options.maxAgeMs ?? retentionDaysToMs(parsePositiveNumber(process.env.NUXT_CSP_TELEMETRY_RETENTION_MAX_AGE_DAYS))
  )
})

const parsePositiveInteger = (value: string | undefined): number | undefined => {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const parsePositiveNumber = (value: string | undefined): number | undefined => {
  const parsed = Number.parseFloat(value ?? '')
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

const retentionDaysToMs = (days: number | undefined): number | undefined =>
  days === undefined ? undefined : days * 24 * 60 * 60 * 1000

const sanitizeStorageError = (error: unknown): string => {
  const message = error instanceof Error ? error.message : 'storage error'
  return message
    .replace(/postgres:\/\/\S+/gi, '[redacted-postgres-url]')
    .replace(/password=[^\s]+/gi, 'password=[redacted]')
    .slice(0, 160)
}

interface PostgresCspTelemetryRow {
  received_at: Date
  request_id: string
  document_uri_origin: string
  blocked_uri_origin: string
  violated_directive: string
  effective_directive: string
  disposition: NormalizedCspReport['disposition']
  user_agent_hash: string
}

const rowToStoredTelemetry = (row: PostgresCspTelemetryRow): StoredCspTelemetry => ({
  receivedAt: row.received_at.toISOString(),
  report: {
    requestId: row.request_id,
    documentUriOrigin: row.document_uri_origin,
    blockedUriOrigin: row.blocked_uri_origin,
    violatedDirective: row.violated_directive,
    effectiveDirective: row.effective_directive,
    disposition: row.disposition,
    userAgentHash: row.user_agent_hash
  }
})

export const defaultCspTelemetryStore = createDefaultCspTelemetryStore()
