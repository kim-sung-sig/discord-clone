# CSP Telemetry Postgres Migration Runbook

Date: 2026-05-20
Scope: Explicit table creation for Nuxt CSP telemetry, CSP rate-limit telemetry, and CSP alert transitions.

## Purpose

Use this runbook before enabling durable CSP dashboard telemetry with `NUXT_CSP_TELEMETRY_POSTGRES_URL`. The Nuxt stores still contain defensive lazy `CREATE TABLE IF NOT EXISTS` guards, but production deployments should apply and review the SQL migration explicitly.

Migration file:

```text
apps/web/server/database/migrations/001_csp_telemetry_postgres.sql
```

## Pre-check

- Confirm the target database is the same database referenced by `NUXT_CSP_TELEMETRY_POSTGRES_URL`.
- Confirm application traffic is drained or the migration is approved for online `CREATE TABLE IF NOT EXISTS` and `CREATE INDEX IF NOT EXISTS` execution.
- Confirm the migration file is reviewed with the release.
- Confirm credentials are passed through environment variables or a secret manager, not shell history.

## Apply

PowerShell:

```powershell
$env:PGDATABASE = '<database>'
$env:PGHOST = '<host>'
$env:PGPORT = '5432'
$env:PGUSER = '<user>'
$env:PGPASSWORD = '<password>'
psql -v ON_ERROR_STOP=1 -f apps/web/server/database/migrations/001_csp_telemetry_postgres.sql
```

Bash:

```bash
export PGDATABASE='<database>'
export PGHOST='<host>'
export PGPORT='5432'
export PGUSER='<user>'
export PGPASSWORD='<password>'
psql -v ON_ERROR_STOP=1 -f apps/web/server/database/migrations/001_csp_telemetry_postgres.sql
```

Docker fallback for the local central `postgres-source` container:

```powershell
Get-Content apps/web/server/database/migrations/001_csp_telemetry_postgres.sql |
  docker exec -i postgres-source psql -U dev_user -d discord -v ON_ERROR_STOP=1
```

## Verification

Confirm all expected tables exist:

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN (
    'csp_telemetry',
    'csp_telemetry_retention_metrics',
    'csp_rate_limit_telemetry',
    'csp_alert_transitions'
  )
ORDER BY table_name;
```

Confirm all expected indexes exist:

```sql
SELECT indexname
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
    'idx_csp_telemetry_received_at',
    'idx_csp_telemetry_effective_directive',
    'idx_csp_rate_limit_telemetry_received_at',
    'idx_csp_alert_transitions_observed_at'
  )
ORDER BY indexname;
```

After setting `NUXT_CSP_TELEMETRY_POSTGRES_URL`, run the gated Postgres telemetry test when Docker/Postgres is available:

```powershell
$env:NUXT_RUN_POSTGRES_TESTS = 'true'
$env:NUXT_CSP_TELEMETRY_POSTGRES_URL = 'postgres://<user>:<password>@<host>:5432/<database>'
npm test --workspace @discord-clone/web -- --run csp-telemetry-postgres.test.ts
```

## Rollback

This migration only creates empty telemetry tables and indexes. Prefer leaving the schema in place and disabling durable telemetry by unsetting `NUXT_CSP_TELEMETRY_POSTGRES_URL`.

If a fresh non-production database must be cleaned up, drop in dependency order:

```sql
DROP TABLE IF EXISTS csp_alert_transitions;
DROP TABLE IF EXISTS csp_rate_limit_telemetry;
DROP TABLE IF EXISTS csp_telemetry_retention_metrics;
DROP TABLE IF EXISTS csp_telemetry;
```

Do not drop these tables in production without an approved data retention decision.
