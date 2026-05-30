# T106 Database-backed Dashboard Telemetry Design

Date: 2026-05-19
PDCA Phase: Design
Slice: T106 Database-backed Dashboard Telemetry

## Architecture Decision

Add a SQLite-backed implementation of the existing `CspTelemetryStore` interface. The Nuxt server keeps the in-memory store by default, and switches to SQLite only when `NUXT_CSP_TELEMETRY_SQLITE_PATH` is configured.

This avoids a new npm dependency and keeps the dashboard contract stable while providing durable local/production-single-node telemetry. PostgreSQL aggregation remains a later task because the current CSP endpoints live in Nuxt, not the Spring Boot backend.

## Component Changes

| Component | Responsibility |
| --- | --- |
| `SqliteCspTelemetryStore` | Create schema, insert normalized reports, read recent reports, compute summary. |
| `createDefaultCspTelemetryStore` | Select SQLite store when DB path is configured, otherwise in-memory. |
| `defaultCspTelemetryStore` | Existing import target used by report ingestion and dashboard routes. |
| `security-headers.test.ts` | RED/GREEN coverage for durable persistence and store selection. |
| T106 docs | Record scope, evidence, residual risks, and re-prioritized task queue. |

## Data Model

SQLite table:

```sql
CREATE TABLE IF NOT EXISTS csp_telemetry (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  received_at TEXT NOT NULL,
  request_id TEXT NOT NULL,
  document_uri_origin TEXT NOT NULL,
  blocked_uri_origin TEXT NOT NULL,
  violated_directive TEXT NOT NULL,
  effective_directive TEXT NOT NULL,
  disposition TEXT NOT NULL,
  user_agent_hash TEXT NOT NULL
);
```

Only normalized report fields are stored. No raw body, full URL, query string, script sample, user-agent string, token, cookie, or authorization value is persisted.

## Runtime Selection

```text
NUXT_CSP_TELEMETRY_SQLITE_PATH unset -> InMemoryCspTelemetryStore
NUXT_CSP_TELEMETRY_SQLITE_PATH set   -> SqliteCspTelemetryStore
```

If SQLite initialization fails, the application should fall back to in-memory and log a warning. This prevents dashboard telemetry from breaking CSP report ingestion.

## Testing

- Create temporary SQLite database.
- Record normalized reports through one store instance.
- Re-open a second store instance against the same file.
- Assert recent reports and summary survive.
- Assert JSON output excludes sensitive strings.
- Assert default factory picks in-memory or SQLite based on configured path.
- Run focused CSP/dashboard tests, full web tests, and Nuxt build.

## Risks

- `node:sqlite` is available in the current Node 24 runtime but still emits an experimental warning.
- SQLite is not a distributed telemetry backend.
- Shared file access across multiple Nuxt processes needs operational validation.
- PostgreSQL-backed telemetry remains the stronger long-term production target.
