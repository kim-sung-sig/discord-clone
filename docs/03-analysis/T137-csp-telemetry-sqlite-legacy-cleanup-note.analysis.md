# T137 CSP Telemetry SQLite Legacy Cleanup Note Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T137 CSP Telemetry SQLite Legacy Cleanup Note

## Findings

| Finding | Result |
| --- | --- |
| T109 replaced active durable telemetry with Postgres | Cleanup guidance now points operators to `NUXT_CSP_TELEMETRY_POSTGRES_URL`. |
| T106/T114 docs still describe SQLite history | Cleanup guidance keeps SQLite as legacy-only context. |
| Importing historical SQLite rows can skew alert metrics | Runbook explicitly says not to import old SQLite telemetry into Postgres. |
| Docs-only cleanup needed validation | Added `qa/csp-sqlite-legacy-cleanup.contract.ps1`. |

## Security Review

Archived SQLite files are treated as telemetry evidence and should be handled as sensitive incident data. The runbook records hashes without dumping telemetry contents.

## Residual Risk

- T110 and T115 remain legacy-only backlog items while old SQLite docs exist.
