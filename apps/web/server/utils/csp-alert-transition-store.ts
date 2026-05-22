import postgres from 'postgres'
import type { CspTelemetryAlert } from './csp-alert-threshold'

type MaybePromise<T> = T | Promise<T>

export interface CspAlertTransition {
  active: boolean
  observedAt: string
  reasons: string[]
}

export interface CspAlertTransitionStore {
  recordIfChanged(alert: CspTelemetryAlert, observedAt: Date): MaybePromise<void>
  recent(limit?: number): MaybePromise<CspAlertTransition[]>
  close?(): MaybePromise<void>
}

export interface InMemoryCspAlertTransitionStoreOptions {
  maxEntries?: number
}

export class InMemoryCspAlertTransitionStore implements CspAlertTransitionStore {
  private readonly maxEntries: number
  private readonly entries: CspAlertTransition[] = []

  constructor(options: InMemoryCspAlertTransitionStoreOptions = {}) {
    this.maxEntries = alertTransitionMaxEntries(options.maxEntries)
  }

  recordIfChanged(alert: CspTelemetryAlert, observedAt: Date): void {
    const next = toTransition(alert, observedAt)
    if (this.entries[0] && sameTransitionState(this.entries[0], next)) {
      return
    }
    this.entries.unshift(next)
    if (this.entries.length > this.maxEntries) {
      this.entries.length = this.maxEntries
    }
  }

  recent(limit = this.maxEntries): CspAlertTransition[] {
    return this.entries.slice(0, boundedLimit(limit, this.maxEntries)).map(copyTransition)
  }
}

export interface PostgresCspAlertTransitionStoreOptions {
  databaseUrl: string
  maxEntries?: number
}

export class PostgresCspAlertTransitionStore implements CspAlertTransitionStore {
  private readonly sql: ReturnType<typeof postgres>
  private readonly maxEntries: number
  private initialized?: Promise<void>

  constructor(options: PostgresCspAlertTransitionStoreOptions) {
    if (!options.databaseUrl.trim()) {
      throw new Error('databaseUrl is required')
    }
    this.maxEntries = alertTransitionMaxEntries(options.maxEntries)
    this.sql = postgres(options.databaseUrl, { max: 3 })
  }

  async recordIfChanged(alert: CspTelemetryAlert, observedAt: Date): Promise<void> {
    await this.ensureSchema()
    const next = toTransition(alert, observedAt)
    const current = await this.latest()
    if (current && sameTransitionState(current, next)) {
      return
    }
    await this.sql`
      INSERT INTO csp_alert_transitions (
        observed_at,
        active,
        reasons_json
      ) VALUES (
        ${next.observedAt},
        ${next.active},
        ${JSON.stringify(next.reasons)}
      )
    `
    await this.prune()
  }

  async recent(limit = this.maxEntries): Promise<CspAlertTransition[]> {
    await this.ensureSchema()
    const rows = await this.sql<CspAlertTransitionRow[]>`
      SELECT observed_at, active, reasons_json
      FROM csp_alert_transitions
      ORDER BY observed_at DESC, id DESC
      LIMIT ${boundedLimit(limit, this.maxEntries)}
    `
    return rows.map(rowToTransition)
  }

  async clearForTests(): Promise<void> {
    await this.ensureSchema()
    await this.sql`TRUNCATE TABLE csp_alert_transitions`
  }

  async close(): Promise<void> {
    await this.sql.end()
  }

  private ensureSchema(): Promise<void> {
    this.initialized ??= (async () => {
      await this.sql`
        CREATE TABLE IF NOT EXISTS csp_alert_transitions (
          id BIGSERIAL PRIMARY KEY,
          observed_at TIMESTAMPTZ NOT NULL,
          active BOOLEAN NOT NULL,
          reasons_json TEXT NOT NULL
        )
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_csp_alert_transitions_observed_at
        ON csp_alert_transitions(observed_at DESC, id DESC)
      `
    })()
    return this.initialized
  }

  private async latest(): Promise<CspAlertTransition | undefined> {
    const rows = await this.sql<CspAlertTransitionRow[]>`
      SELECT observed_at, active, reasons_json
      FROM csp_alert_transitions
      ORDER BY observed_at DESC, id DESC
      LIMIT 1
    `
    return rows[0] ? rowToTransition(rows[0]) : undefined
  }

  private async prune(): Promise<void> {
    await this.sql`
      DELETE FROM csp_alert_transitions
      WHERE id NOT IN (
        SELECT id
        FROM csp_alert_transitions
        ORDER BY observed_at DESC, id DESC
        LIMIT ${this.maxEntries}
      )
    `
  }
}

export interface DefaultCspAlertTransitionStoreOptions {
  databaseUrl?: string
  maxEntries?: number
}

export const createDefaultCspAlertTransitionStore = (
  options: DefaultCspAlertTransitionStoreOptions = {}
): CspAlertTransitionStore => {
  const databaseUrl = options.databaseUrl?.trim()
    || process.env.NUXT_CSP_ALERT_POSTGRES_URL?.trim()
    || process.env.NUXT_CSP_TELEMETRY_POSTGRES_URL?.trim()
  const maxEntries = alertTransitionMaxEntries(
    options.maxEntries ?? parsePositiveInteger(process.env.NUXT_CSP_ALERT_TRANSITION_MAX_ENTRIES)
  )
  if (databaseUrl) {
    return new PostgresCspAlertTransitionStore({ databaseUrl, maxEntries })
  }
  return new InMemoryCspAlertTransitionStore({ maxEntries })
}

const toTransition = (alert: CspTelemetryAlert, observedAt: Date): CspAlertTransition => ({
  active: alert.active,
  observedAt: observedAt.toISOString(),
  reasons: [...alert.reasons]
})

const sameTransitionState = (left: CspAlertTransition, right: CspAlertTransition): boolean =>
  left.active === right.active && JSON.stringify(left.reasons) === JSON.stringify(right.reasons)

const copyTransition = (transition: CspAlertTransition): CspAlertTransition => ({
  active: transition.active,
  observedAt: transition.observedAt,
  reasons: [...transition.reasons]
})

const alertTransitionMaxEntries = (value: number | undefined): number => {
  if (!Number.isFinite(value)) {
    return 200
  }
  return Math.max(1, Math.trunc(value ?? 200))
}

const boundedLimit = (value: number, maxEntries: number): number =>
  Math.min(Math.max(Math.trunc(value), 0), maxEntries)

const parsePositiveInteger = (value: string | undefined): number | undefined => {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

interface CspAlertTransitionRow {
  observed_at: string
  active: boolean
  reasons_json: string
}

const rowToTransition = (row: CspAlertTransitionRow): CspAlertTransition => ({
  active: row.active,
  observedAt: new Date(row.observed_at).toISOString(),
  reasons: parseReasons(row.reasons_json)
})

const parseReasons = (value: string): string[] => {
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.filter((reason): reason is string => typeof reason === 'string') : []
  } catch {
    return []
  }
}

export const defaultCspAlertTransitionStore = createDefaultCspAlertTransitionStore()
