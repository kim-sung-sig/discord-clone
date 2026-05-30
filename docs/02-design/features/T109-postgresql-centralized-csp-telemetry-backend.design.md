# T109 PostgreSQL Centralized CSP Telemetry Backend Design

Date: 2026-05-19
Slice: T109 PostgreSQL Centralized CSP Telemetry Backend

## Architecture

The Nuxt server continues to own CSP report normalization and dashboard reads. The storage implementation changes from local SQLite to central PostgreSQL when `NUXT_CSP_TELEMETRY_POSTGRES_URL` is configured.

## Components

- `PostgresCspTelemetryStore`
  - Uses the `postgres` Node client.
  - Creates telemetry and retention metric tables on first use.
  - Persists sanitized reports only.
  - Enforces max-age and max-entry retention after each write.

- `createDefaultCspTelemetryStore`
  - Selects Postgres when `databaseUrl` or `NUXT_CSP_TELEMETRY_POSTGRES_URL` is present.
  - Falls back to in-memory storage for local lightweight runs.

- Async dashboard path
  - `buildCspTelemetryDashboard` awaits store summary, retention metrics, and recent reports.
  - `/api/security/csp-telemetry` awaits the dashboard builder.
  - CSP report routes already use the async handler path.

## Database

Central database:

```text
host: 127.0.0.1
port: 15432
database: discord
user: dev_user
password: dev_password
```

Runtime URL:

```text
NUXT_CSP_TELEMETRY_POSTGRES_URL=postgres://dev_user:dev_password@127.0.0.1:15432/discord
```

## Testing

The Postgres integration test is gated by `NUXT_RUN_POSTGRES_TESTS=true` so normal web tests do not require Docker. The enabled test verifies central writes, reads, summary counts, and retention metrics.
