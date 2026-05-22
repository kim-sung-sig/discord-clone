import postgres from 'postgres'
import type { CspAlertAcknowledgementStatus } from './csp-alert-acknowledgement-store'

type MaybePromise<T> = T | Promise<T>

export type CspAlertIncidentEventType = 'acknowledged' | 'snoozed' | 'assigned' | 'status_changed'

export interface CspAlertIncidentEvent {
  fingerprint: string
  eventType: CspAlertIncidentEventType
  status: CspAlertAcknowledgementStatus
  actor: string
  assignedTo?: string
  reason?: string
  occurredAt: string
  snoozeUntil?: string
}

export interface CspAlertIncidentStore {
  append(event: CspAlertIncidentEvent): MaybePromise<CspAlertIncidentEvent>
  recent(fingerprint?: string, limit?: number): MaybePromise<CspAlertIncidentEvent[]>
  close?(): MaybePromise<void>
}

export interface InMemoryCspAlertIncidentStoreOptions {
  maxEntries?: number
}

export class InMemoryCspAlertIncidentStore implements CspAlertIncidentStore {
  private readonly maxEntries: number
  private readonly events: CspAlertIncidentEvent[] = []

  constructor(options: InMemoryCspAlertIncidentStoreOptions = {}) {
    this.maxEntries = alertIncidentMaxEntries(options.maxEntries)
  }

  append(event: CspAlertIncidentEvent): CspAlertIncidentEvent {
    const stored = copyIncidentEvent(event)
    this.events.unshift(stored)
    if (this.events.length > this.maxEntries) {
      this.events.length = this.maxEntries
    }
    return copyIncidentEvent(stored)
  }

  recent(fingerprint?: string, limit = this.maxEntries): CspAlertIncidentEvent[] {
    return this.events
      .filter((event) => !fingerprint || event.fingerprint === fingerprint)
      .slice(0, boundedLimit(limit, this.maxEntries))
      .map(copyIncidentEvent)
  }
}

export interface PostgresCspAlertIncidentStoreOptions {
  databaseUrl: string
  maxEntries?: number
}

export class PostgresCspAlertIncidentStore implements CspAlertIncidentStore {
  private readonly sql: ReturnType<typeof postgres>
  private readonly maxEntries: number
  private initialized?: Promise<void>

  constructor(options: PostgresCspAlertIncidentStoreOptions) {
    if (!options.databaseUrl.trim()) {
      throw new Error('databaseUrl is required')
    }
    this.maxEntries = alertIncidentMaxEntries(options.maxEntries)
    this.sql = postgres(options.databaseUrl, { max: 3 })
  }

  async append(event: CspAlertIncidentEvent): Promise<CspAlertIncidentEvent> {
    await this.ensureSchema()
    const stored = copyIncidentEvent(event)
    await this.sql`
      INSERT INTO csp_alert_incident_events (
        fingerprint,
        event_type,
        status,
        actor,
        assigned_to,
        reason,
        occurred_at,
        snooze_until
      ) VALUES (
        ${stored.fingerprint},
        ${stored.eventType},
        ${stored.status},
        ${stored.actor},
        ${stored.assignedTo ?? null},
        ${stored.reason ?? null},
        ${stored.occurredAt},
        ${stored.snoozeUntil ?? null}
      )
    `
    await this.prune()
    return stored
  }

  async recent(fingerprint?: string, limit = this.maxEntries): Promise<CspAlertIncidentEvent[]> {
    await this.ensureSchema()
    const bounded = boundedLimit(limit, this.maxEntries)
    const rows = fingerprint
      ? await this.sql<CspAlertIncidentEventRow[]>`
        SELECT fingerprint, event_type, status, actor, assigned_to, reason, occurred_at, snooze_until
        FROM csp_alert_incident_events
        WHERE fingerprint = ${fingerprint}
        ORDER BY occurred_at DESC, id DESC
        LIMIT ${bounded}
      `
      : await this.sql<CspAlertIncidentEventRow[]>`
        SELECT fingerprint, event_type, status, actor, assigned_to, reason, occurred_at, snooze_until
        FROM csp_alert_incident_events
        ORDER BY occurred_at DESC, id DESC
        LIMIT ${bounded}
      `
    return rows.map(rowToIncidentEvent)
  }

  async clearForTests(): Promise<void> {
    await this.ensureSchema()
    await this.sql`TRUNCATE TABLE csp_alert_incident_events`
  }

  async close(): Promise<void> {
    await this.sql.end()
  }

  private ensureSchema(): Promise<void> {
    this.initialized ??= (async () => {
      await this.sql`
        CREATE TABLE IF NOT EXISTS csp_alert_incident_events (
          id BIGSERIAL PRIMARY KEY,
          fingerprint TEXT NOT NULL,
          event_type TEXT NOT NULL,
          status TEXT NOT NULL,
          actor TEXT NOT NULL,
          assigned_to TEXT,
          reason TEXT,
          occurred_at TIMESTAMPTZ NOT NULL,
          snooze_until TIMESTAMPTZ
        )
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_csp_alert_incident_events_fingerprint_occurred_at
        ON csp_alert_incident_events(fingerprint, occurred_at DESC, id DESC)
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_csp_alert_incident_events_occurred_at
        ON csp_alert_incident_events(occurred_at DESC, id DESC)
      `
    })()
    return this.initialized
  }

  private async prune(): Promise<void> {
    await this.sql`
      DELETE FROM csp_alert_incident_events
      WHERE id NOT IN (
        SELECT id
        FROM csp_alert_incident_events
        ORDER BY occurred_at DESC, id DESC
        LIMIT ${this.maxEntries}
      )
    `
  }
}

export interface DefaultCspAlertIncidentStoreOptions {
  databaseUrl?: string
  maxEntries?: number
}

export const createDefaultCspAlertIncidentStore = (
  options: DefaultCspAlertIncidentStoreOptions = {}
): CspAlertIncidentStore => {
  const databaseUrl = options.databaseUrl?.trim()
    || process.env.NUXT_CSP_ALERT_INCIDENT_POSTGRES_URL?.trim()
    || process.env.NUXT_CSP_ALERT_POSTGRES_URL?.trim()
    || process.env.NUXT_CSP_TELEMETRY_POSTGRES_URL?.trim()
  const maxEntries = alertIncidentMaxEntries(
    options.maxEntries ?? parsePositiveInteger(process.env.NUXT_CSP_ALERT_INCIDENT_MAX_ENTRIES)
  )
  if (databaseUrl) {
    return new PostgresCspAlertIncidentStore({ databaseUrl, maxEntries })
  }
  return new InMemoryCspAlertIncidentStore({ maxEntries })
}

const copyIncidentEvent = (event: CspAlertIncidentEvent): CspAlertIncidentEvent => ({
  fingerprint: event.fingerprint,
  eventType: event.eventType,
  status: event.status,
  actor: event.actor,
  ...(event.assignedTo ? { assignedTo: event.assignedTo } : {}),
  ...(event.reason ? { reason: event.reason } : {}),
  occurredAt: event.occurredAt,
  ...(event.snoozeUntil ? { snoozeUntil: event.snoozeUntil } : {})
})

const alertIncidentMaxEntries = (value: number | undefined): number => {
  if (!Number.isFinite(value)) {
    return 500
  }
  return Math.max(1, Math.trunc(value ?? 500))
}

const boundedLimit = (value: number, maxEntries: number): number =>
  Math.min(Math.max(Math.trunc(value), 0), maxEntries)

const parsePositiveInteger = (value: string | undefined): number | undefined => {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined
}

interface CspAlertIncidentEventRow {
  fingerprint: string
  event_type: CspAlertIncidentEventType
  status: CspAlertAcknowledgementStatus
  actor: string
  assigned_to: string | null
  reason: string | null
  occurred_at: string
  snooze_until: string | null
}

const rowToIncidentEvent = (row: CspAlertIncidentEventRow): CspAlertIncidentEvent => ({
  fingerprint: row.fingerprint,
  eventType: row.event_type,
  status: row.status,
  actor: row.actor,
  ...(row.assigned_to ? { assignedTo: row.assigned_to } : {}),
  ...(row.reason ? { reason: row.reason } : {}),
  occurredAt: new Date(row.occurred_at).toISOString(),
  ...(row.snooze_until ? { snoozeUntil: new Date(row.snooze_until).toISOString() } : {})
})

export const defaultCspAlertIncidentStore = createDefaultCspAlertIncidentStore()
