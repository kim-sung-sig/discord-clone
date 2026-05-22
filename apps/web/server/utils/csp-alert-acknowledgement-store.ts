import { createHash } from 'node:crypto'
import postgres from 'postgres'
import type { CspTelemetryAlert } from './csp-alert-threshold'
import type { CspAlertIncidentStore } from './csp-alert-incident-store'

type MaybePromise<T> = T | Promise<T>

export type CspAlertAcknowledgementStatus = 'unacknowledged' | 'acknowledged' | 'snoozed'

export interface CspAlertAcknowledgement {
  fingerprint: string
  status: CspAlertAcknowledgementStatus
  reason?: string
  acknowledgedBy?: string
  acknowledgedAt?: string
  snoozeUntil?: string
}

export interface CspAlertAcknowledgementCommand {
  fingerprint: string
  reason: string
  acknowledgedBy: string
  acknowledgedAt: Date
  snoozeUntil?: Date
}

export interface CspAlertAcknowledgementStore {
  acknowledge(command: CspAlertAcknowledgementCommand): MaybePromise<CspAlertAcknowledgement>
  current(fingerprint: string, now: Date): MaybePromise<CspAlertAcknowledgement>
  close?(): MaybePromise<void>
}

export interface CspAlertAcknowledgeInput {
  alert: CspTelemetryAlert
  store: CspAlertAcknowledgementStore
  incidentStore?: CspAlertIncidentStore
  reason: string
  acknowledgedBy: string
  snoozeMinutes?: number
  now?: () => Date
}

export const cspAlertFingerprint = (alert: Pick<CspTelemetryAlert, 'active' | 'reasons'>): string => {
  if (!alert.active || alert.reasons.length === 0) {
    return ''
  }
  return createHash('sha256')
    .update(alert.reasons.map((reason) => reason.trim()).sort().join('\n'))
    .digest('hex')
    .slice(0, 24)
}

export const unacknowledgedCspAlert = (fingerprint: string): CspAlertAcknowledgement => ({
  fingerprint,
  status: 'unacknowledged'
})

export const acknowledgeCspAlert = async ({
  alert,
  store,
  incidentStore,
  reason,
  acknowledgedBy,
  snoozeMinutes,
  now = () => new Date()
}: CspAlertAcknowledgeInput): Promise<CspAlertAcknowledgement> => {
  if (!alert.active) {
    throw new Error('only active CSP alerts can be acknowledged')
  }
  const fingerprint = alert.fingerprint || cspAlertFingerprint(alert)
  if (!fingerprint) {
    throw new Error('active CSP alert fingerprint is required')
  }
  const cleanedReason = sanitizeReason(reason)
  const acknowledgedAt = now()
  const boundedSnoozeMinutes = sanitizeSnoozeMinutes(snoozeMinutes)
  const sanitizedAcknowledgedBy = sanitizeAcknowledgedBy(acknowledgedBy)
  const acknowledgement = await store.acknowledge({
    fingerprint,
    reason: cleanedReason,
    acknowledgedBy: sanitizedAcknowledgedBy,
    acknowledgedAt,
    ...(boundedSnoozeMinutes
      ? { snoozeUntil: new Date(acknowledgedAt.getTime() + boundedSnoozeMinutes * 60_000) }
      : {})
  })
  if (incidentStore) {
    await incidentStore.append({
      fingerprint,
      eventType: acknowledgement.status === 'snoozed' ? 'snoozed' : 'acknowledged',
      status: acknowledgement.status,
      actor: sanitizedAcknowledgedBy,
      assignedTo: sanitizedAcknowledgedBy,
      reason: cleanedReason,
      occurredAt: acknowledgement.acknowledgedAt ?? acknowledgedAt.toISOString(),
      ...(acknowledgement.snoozeUntil ? { snoozeUntil: acknowledgement.snoozeUntil } : {})
    })
  }
  return acknowledgement
}

export class InMemoryCspAlertAcknowledgementStore implements CspAlertAcknowledgementStore {
  private readonly acknowledgements = new Map<string, StoredCspAlertAcknowledgement>()

  acknowledge(command: CspAlertAcknowledgementCommand): CspAlertAcknowledgement {
    const stored = toStoredAcknowledgement(command)
    this.acknowledgements.set(command.fingerprint, stored)
    return toAcknowledgement(stored, command.acknowledgedAt)
  }

  current(fingerprint: string, now: Date): CspAlertAcknowledgement {
    const stored = this.acknowledgements.get(fingerprint)
    return stored ? toAcknowledgement(stored, now) : unacknowledgedCspAlert(fingerprint)
  }
}

export interface PostgresCspAlertAcknowledgementStoreOptions {
  databaseUrl: string
}

export class PostgresCspAlertAcknowledgementStore implements CspAlertAcknowledgementStore {
  private readonly sql: ReturnType<typeof postgres>
  private initialized?: Promise<void>

  constructor(options: PostgresCspAlertAcknowledgementStoreOptions) {
    if (!options.databaseUrl.trim()) {
      throw new Error('databaseUrl is required')
    }
    this.sql = postgres(options.databaseUrl, { max: 3 })
  }

  async acknowledge(command: CspAlertAcknowledgementCommand): Promise<CspAlertAcknowledgement> {
    await this.ensureSchema()
    const stored = toStoredAcknowledgement(command)
    await this.sql`
      INSERT INTO csp_alert_acknowledgements (
        fingerprint,
        reason,
        acknowledged_by,
        acknowledged_at,
        snooze_until
      ) VALUES (
        ${stored.fingerprint},
        ${stored.reason},
        ${stored.acknowledgedBy},
        ${stored.acknowledgedAt},
        ${stored.snoozeUntil ?? null}
      )
      ON CONFLICT(fingerprint) DO UPDATE SET
        reason = excluded.reason,
        acknowledged_by = excluded.acknowledged_by,
        acknowledged_at = excluded.acknowledged_at,
        snooze_until = excluded.snooze_until
    `
    return toAcknowledgement(stored, command.acknowledgedAt)
  }

  async current(fingerprint: string, now: Date): Promise<CspAlertAcknowledgement> {
    await this.ensureSchema()
    const rows = await this.sql<CspAlertAcknowledgementRow[]>`
      SELECT fingerprint, reason, acknowledged_by, acknowledged_at, snooze_until
      FROM csp_alert_acknowledgements
      WHERE fingerprint = ${fingerprint}
      LIMIT 1
    `
    return rows[0] ? toAcknowledgement(rowToStored(rows[0]), now) : unacknowledgedCspAlert(fingerprint)
  }

  async close(): Promise<void> {
    await this.sql.end()
  }

  async clearForTests(): Promise<void> {
    await this.ensureSchema()
    await this.sql`DELETE FROM csp_alert_acknowledgements`
  }

  private ensureSchema(): Promise<void> {
    this.initialized ??= (async () => {
      await this.sql`
        CREATE TABLE IF NOT EXISTS csp_alert_acknowledgements (
          fingerprint TEXT PRIMARY KEY,
          reason TEXT NOT NULL,
          acknowledged_by TEXT NOT NULL,
          acknowledged_at TIMESTAMPTZ NOT NULL,
          snooze_until TIMESTAMPTZ
        )
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_csp_alert_acknowledgements_acknowledged_at
        ON csp_alert_acknowledgements(acknowledged_at DESC)
      `
    })()
    return this.initialized
  }
}

export interface DefaultCspAlertAcknowledgementStoreOptions {
  databaseUrl?: string
}

export const createDefaultCspAlertAcknowledgementStore = (
  options: DefaultCspAlertAcknowledgementStoreOptions = {}
): CspAlertAcknowledgementStore => {
  const databaseUrl = options.databaseUrl?.trim()
    || process.env.NUXT_CSP_ALERT_ACK_POSTGRES_URL?.trim()
    || process.env.NUXT_CSP_TELEMETRY_POSTGRES_URL?.trim()
  if (databaseUrl) {
    return new PostgresCspAlertAcknowledgementStore({ databaseUrl })
  }
  return new InMemoryCspAlertAcknowledgementStore()
}

const sanitizeReason = (reason: string): string => {
  const cleaned = reason.trim().replace(/\s+/g, ' ').slice(0, 120)
  if (!cleaned) {
    throw new Error('acknowledgement reason is required')
  }
  return cleaned
}

const sanitizeAcknowledgedBy = (acknowledgedBy: string): string => {
  const cleaned = acknowledgedBy.trim().replace(/\s+/g, ' ').slice(0, 80)
  return cleaned || 'unknown-operator'
}

const sanitizeSnoozeMinutes = (snoozeMinutes: number | undefined): number | undefined => {
  if (snoozeMinutes === undefined || snoozeMinutes === null || Number.isNaN(snoozeMinutes)) {
    return undefined
  }
  const parsed = Math.trunc(snoozeMinutes)
  if (parsed < 1 || parsed > 1_440) {
    throw new Error('snoozeMinutes must be between 1 and 1440')
  }
  return parsed
}

interface StoredCspAlertAcknowledgement {
  fingerprint: string
  reason: string
  acknowledgedBy: string
  acknowledgedAt: string
  snoozeUntil?: string
}

interface CspAlertAcknowledgementRow {
  fingerprint: string
  reason: string
  acknowledged_by: string
  acknowledged_at: Date
  snooze_until: Date | null
}

const toStoredAcknowledgement = (command: CspAlertAcknowledgementCommand): StoredCspAlertAcknowledgement => ({
  fingerprint: command.fingerprint,
  reason: command.reason,
  acknowledgedBy: command.acknowledgedBy,
  acknowledgedAt: command.acknowledgedAt.toISOString(),
  ...(command.snoozeUntil ? { snoozeUntil: command.snoozeUntil.toISOString() } : {})
})

const rowToStored = (row: CspAlertAcknowledgementRow): StoredCspAlertAcknowledgement => ({
  fingerprint: row.fingerprint,
  reason: row.reason,
  acknowledgedBy: row.acknowledged_by,
  acknowledgedAt: row.acknowledged_at.toISOString(),
  ...(row.snooze_until ? { snoozeUntil: row.snooze_until.toISOString() } : {})
})

const toAcknowledgement = (
  stored: StoredCspAlertAcknowledgement,
  now: Date
): CspAlertAcknowledgement => ({
  fingerprint: stored.fingerprint,
  status: stored.snoozeUntil && new Date(stored.snoozeUntil).getTime() > now.getTime()
    ? 'snoozed'
    : 'acknowledged',
  reason: stored.reason,
  acknowledgedBy: stored.acknowledgedBy,
  acknowledgedAt: stored.acknowledgedAt,
  ...(stored.snoozeUntil ? { snoozeUntil: stored.snoozeUntil } : {})
})

export const defaultCspAlertAcknowledgementStore = createDefaultCspAlertAcknowledgementStore()
