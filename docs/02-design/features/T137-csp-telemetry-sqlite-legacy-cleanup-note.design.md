# T137 CSP Telemetry SQLite Legacy Cleanup Note Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T137 CSP Telemetry SQLite Legacy Cleanup Note

## Runbook

Path: `docs/runbooks/csp-telemetry-sqlite-legacy-cleanup.md`

Sections:

- Purpose
- Pre-check
- Locate files
- Archive
- Delete
- Verification
- Rollback

## Cleanup Policy

- Active durable telemetry should use `NUXT_CSP_TELEMETRY_POSTGRES_URL`.
- `NUXT_CSP_TELEMETRY_SQLITE_PATH` should be absent from active runtime config.
- Old `.sqlite` files may be archived with `sha256` evidence or deleted after Postgres verification.
- Old SQLite telemetry should not be imported into Postgres because it can distort current aggregate alert and retention metrics.

## Security Review

The runbook treats legacy telemetry as incident data. It avoids copying data into active Postgres telemetry, recommends immutable archive handling, and avoids exposing file contents in normal cleanup commands.
