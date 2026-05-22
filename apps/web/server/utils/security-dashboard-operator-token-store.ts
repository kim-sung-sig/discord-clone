import { createHash, randomBytes } from 'node:crypto'
import postgres from 'postgres'

type MaybePromise<T> = T | Promise<T>

export type SecurityDashboardOperatorTokenAuditAction = 'issued' | 'revoked'

export interface SecurityDashboardIssuedOperatorToken {
  token: string
  tokenId: string
  expiresAt: string
}

export interface SecurityDashboardOperatorTokenRecord {
  tokenHash: string
  actor: string
  issuedAt: string
  expiresAt: string
  revokedAt?: string
}

export interface SecurityDashboardOperatorTokenAuditEntry {
  action: SecurityDashboardOperatorTokenAuditAction
  tokenId: string
  actor: string
  at: string
  reason?: string
}

export interface SecurityDashboardOperatorTokenStore {
  issue(command: SecurityDashboardOperatorTokenIssueCommand): MaybePromise<SecurityDashboardIssuedOperatorToken>
  verify(token: string, now: Date): MaybePromise<SecurityDashboardOperatorTokenVerification>
  revoke(command: SecurityDashboardOperatorTokenRevokeCommand): MaybePromise<boolean>
  audit?(limit?: number): MaybePromise<SecurityDashboardOperatorTokenAuditEntry[]>
  close?(): MaybePromise<void>
}

export interface SecurityDashboardOperatorTokenIssueCommand {
  actor: string
  issuedAt: Date
  expiresAt: Date
  token?: string
}

export interface SecurityDashboardOperatorTokenRevokeCommand {
  token: string
  actor: string
  reason?: string
  now: Date
}

export interface SecurityDashboardOperatorTokenVerification {
  valid: boolean
  tokenId?: string
  actor?: string
  reason?: string
}

export interface IssueSecurityDashboardOperatorTokenInput {
  store: SecurityDashboardOperatorTokenStore
  actor: string
  ttlMinutes?: number
  now?: () => Date
}

export const issueSecurityDashboardOperatorToken = async ({
  store,
  actor,
  ttlMinutes = 15,
  now = () => new Date()
}: IssueSecurityDashboardOperatorTokenInput): Promise<SecurityDashboardIssuedOperatorToken> => {
  const issuedAt = now()
  const boundedTtlMinutes = Math.min(Math.max(Math.trunc(ttlMinutes), 1), 60)
  return await store.issue({
    actor: sanitizeActor(actor),
    issuedAt,
    expiresAt: new Date(issuedAt.getTime() + boundedTtlMinutes * 60_000)
  })
}

export class InMemorySecurityDashboardOperatorTokenStore implements SecurityDashboardOperatorTokenStore {
  private readonly records = new Map<string, SecurityDashboardOperatorTokenRecord>()
  private readonly auditEntries: SecurityDashboardOperatorTokenAuditEntry[] = []

  issue(command: SecurityDashboardOperatorTokenIssueCommand): SecurityDashboardIssuedOperatorToken {
    const token = command.token ?? randomOperatorToken()
    const tokenHash = hashToken(token)
    const tokenId = tokenHash.slice(0, 12)
    this.records.set(tokenHash, {
      tokenHash,
      actor: sanitizeActor(command.actor),
      issuedAt: command.issuedAt.toISOString(),
      expiresAt: command.expiresAt.toISOString()
    })
    this.auditEntries.unshift({
      action: 'issued',
      tokenId,
      actor: sanitizeActor(command.actor),
      at: command.issuedAt.toISOString()
    })
    return {
      token,
      tokenId,
      expiresAt: command.expiresAt.toISOString()
    }
  }

  verify(token: string, now: Date): SecurityDashboardOperatorTokenVerification {
    const record = this.records.get(hashToken(token))
    if (!record || record.revokedAt || new Date(record.expiresAt).getTime() <= now.getTime()) {
      return {
        valid: false,
        reason: 'operator token is invalid'
      }
    }
    return {
      valid: true,
      tokenId: record.tokenHash.slice(0, 12),
      actor: record.actor
    }
  }

  revoke(command: SecurityDashboardOperatorTokenRevokeCommand): boolean {
    const tokenHash = hashToken(command.token)
    const record = this.records.get(tokenHash)
    if (!record || record.revokedAt) {
      return false
    }
    record.revokedAt = command.now.toISOString()
    this.auditEntries.unshift({
      action: 'revoked',
      tokenId: tokenHash.slice(0, 12),
      actor: sanitizeActor(command.actor),
      at: command.now.toISOString(),
      ...(command.reason ? { reason: sanitizeReason(command.reason) } : {})
    })
    return true
  }

  audit(limit = 25): SecurityDashboardOperatorTokenAuditEntry[] {
    return this.auditEntries.slice(0, Math.min(Math.max(Math.trunc(limit), 1), 100))
  }
}

export interface PostgresSecurityDashboardOperatorTokenStoreOptions {
  databaseUrl: string
}

export class PostgresSecurityDashboardOperatorTokenStore implements SecurityDashboardOperatorTokenStore {
  private readonly sql: ReturnType<typeof postgres>
  private initialized?: Promise<void>

  constructor(options: PostgresSecurityDashboardOperatorTokenStoreOptions) {
    if (!options.databaseUrl.trim()) {
      throw new Error('databaseUrl is required')
    }
    this.sql = postgres(options.databaseUrl, { max: 3 })
  }

  async issue(command: SecurityDashboardOperatorTokenIssueCommand): Promise<SecurityDashboardIssuedOperatorToken> {
    await this.ensureSchema()
    const token = command.token ?? randomOperatorToken()
    const tokenHash = hashToken(token)
    const tokenId = tokenHash.slice(0, 12)
    const actor = sanitizeActor(command.actor)
    await this.sql`
      INSERT INTO security_dashboard_operator_tokens (
        token_hash,
        actor,
        issued_at,
        expires_at,
        revoked_at
      ) VALUES (
        ${tokenHash},
        ${actor},
        ${command.issuedAt.toISOString()},
        ${command.expiresAt.toISOString()},
        NULL
      )
      ON CONFLICT(token_hash) DO UPDATE SET
        actor = excluded.actor,
        issued_at = excluded.issued_at,
        expires_at = excluded.expires_at,
        revoked_at = NULL
    `
    await this.recordAudit({
      action: 'issued',
      tokenId,
      actor,
      at: command.issuedAt.toISOString()
    })
    return {
      token,
      tokenId,
      expiresAt: command.expiresAt.toISOString()
    }
  }

  async verify(token: string, now: Date): Promise<SecurityDashboardOperatorTokenVerification> {
    await this.ensureSchema()
    const tokenHash = hashToken(token)
    const rows = await this.sql<Array<{
      actor: string
      expires_at: Date
      revoked_at: Date | null
    }>>`
      SELECT actor, expires_at, revoked_at
      FROM security_dashboard_operator_tokens
      WHERE token_hash = ${tokenHash}
      LIMIT 1
    `
    const row = rows[0]
    if (!row || row.revoked_at || row.expires_at.getTime() <= now.getTime()) {
      return {
        valid: false,
        reason: 'operator token is invalid'
      }
    }
    return {
      valid: true,
      tokenId: tokenHash.slice(0, 12),
      actor: row.actor
    }
  }

  async revoke(command: SecurityDashboardOperatorTokenRevokeCommand): Promise<boolean> {
    await this.ensureSchema()
    const tokenHash = hashToken(command.token)
    const rows = await this.sql<Array<{ token_hash: string }>>`
      UPDATE security_dashboard_operator_tokens
      SET revoked_at = ${command.now.toISOString()}
      WHERE token_hash = ${tokenHash}
        AND revoked_at IS NULL
      RETURNING token_hash
    `
    if (rows.length === 0) {
      return false
    }
    await this.recordAudit({
      action: 'revoked',
      tokenId: tokenHash.slice(0, 12),
      actor: sanitizeActor(command.actor),
      at: command.now.toISOString(),
      ...(command.reason ? { reason: sanitizeReason(command.reason) } : {})
    })
    return true
  }

  async audit(limit = 25): Promise<SecurityDashboardOperatorTokenAuditEntry[]> {
    await this.ensureSchema()
    const boundedLimit = Math.min(Math.max(Math.trunc(limit), 1), 100)
    const rows = await this.sql<Array<{
      action: SecurityDashboardOperatorTokenAuditAction
      token_hash_prefix: string
      actor: string
      occurred_at: Date
      reason: string | null
    }>>`
      SELECT action, token_hash_prefix, actor, occurred_at, reason
      FROM security_dashboard_operator_token_audit
      ORDER BY occurred_at DESC, id DESC
      LIMIT ${boundedLimit}
    `
    return rows.map((row) => ({
      action: row.action,
      tokenId: row.token_hash_prefix,
      actor: row.actor,
      at: row.occurred_at.toISOString(),
      ...(row.reason ? { reason: row.reason } : {})
    }))
  }

  async clearForTests(): Promise<void> {
    await this.ensureSchema()
    await this.sql`TRUNCATE TABLE security_dashboard_operator_token_audit, security_dashboard_operator_tokens`
  }

  async close(): Promise<void> {
    await this.sql.end()
  }

  private ensureSchema(): Promise<void> {
    this.initialized ??= (async () => {
      await this.sql`
        CREATE TABLE IF NOT EXISTS security_dashboard_operator_tokens (
          token_hash TEXT PRIMARY KEY,
          actor TEXT NOT NULL,
          issued_at TIMESTAMPTZ NOT NULL,
          expires_at TIMESTAMPTZ NOT NULL,
          revoked_at TIMESTAMPTZ
        )
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_security_dashboard_operator_tokens_expires_at
        ON security_dashboard_operator_tokens(expires_at)
      `
      await this.sql`
        CREATE TABLE IF NOT EXISTS security_dashboard_operator_token_audit (
          id BIGSERIAL PRIMARY KEY,
          action TEXT NOT NULL,
          token_hash_prefix TEXT NOT NULL,
          actor TEXT NOT NULL,
          occurred_at TIMESTAMPTZ NOT NULL,
          reason TEXT
        )
      `
      await this.sql`
        CREATE INDEX IF NOT EXISTS idx_security_dashboard_operator_token_audit_occurred_at
        ON security_dashboard_operator_token_audit(occurred_at DESC, id DESC)
      `
    })()
    return this.initialized
  }

  private async recordAudit(entry: SecurityDashboardOperatorTokenAuditEntry): Promise<void> {
    await this.sql`
      INSERT INTO security_dashboard_operator_token_audit (
        action,
        token_hash_prefix,
        actor,
        occurred_at,
        reason
      ) VALUES (
        ${entry.action},
        ${entry.tokenId},
        ${entry.actor},
        ${entry.at},
        ${entry.reason ?? null}
      )
    `
  }
}

export const hashSecurityDashboardOperatorTokenForTests = (token: string): string => hashToken(token)

export interface DefaultSecurityDashboardOperatorTokenStoreOptions {
  databaseUrl?: string
}

export const createDefaultSecurityDashboardOperatorTokenStore = (
  options: DefaultSecurityDashboardOperatorTokenStoreOptions = {}
): SecurityDashboardOperatorTokenStore => {
  const databaseUrl = options.databaseUrl?.trim()
    || process.env.NUXT_SECURITY_DASHBOARD_OPERATOR_TOKEN_POSTGRES_URL?.trim()
    || process.env.NUXT_CSP_TELEMETRY_POSTGRES_URL?.trim()
  if (databaseUrl) {
    return new PostgresSecurityDashboardOperatorTokenStore({ databaseUrl })
  }
  return new InMemorySecurityDashboardOperatorTokenStore()
}

export const defaultSecurityDashboardOperatorTokenStore = createDefaultSecurityDashboardOperatorTokenStore()

const randomOperatorToken = (): string => `sdo_${randomBytes(32).toString('base64url')}`

const hashToken = (token: string): string =>
  createHash('sha256')
    .update(token.trim() || 'missing-operator-token')
    .digest('hex')

const sanitizeActor = (actor: string): string => actor.trim().replace(/\s+/g, ' ').slice(0, 80) || 'operator'

const sanitizeReason = (reason: string): string => reason.trim().replace(/\s+/g, ' ').slice(0, 120)
