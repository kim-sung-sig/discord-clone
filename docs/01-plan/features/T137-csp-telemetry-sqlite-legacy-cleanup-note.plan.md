# T137 CSP Telemetry SQLite Legacy Cleanup Note Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T137 CSP Telemetry SQLite Legacy Cleanup Note

## Executive Summary

| View | Content |
| --- | --- |
| Problem | After Postgres migration, old local SQLite CSP telemetry files can remain without archive/delete guidance. |
| Solution | Add a legacy cleanup runbook and contract for locating, archiving, verifying, and deleting old SQLite telemetry files. |
| Operator Effect | Operators can clean up obsolete local files without polluting Postgres telemetry or losing incident evidence. |
| Core Value | Migration cleanup becomes explicit and auditable. |

## Scope

- Add `docs/runbooks/csp-telemetry-sqlite-legacy-cleanup.md`.
- Include locate, archive, delete, verification, and rollback guidance.
- Add a contract script for required cleanup content.

## Out of Scope

- Reintroducing SQLite runtime support.
- Importing old SQLite telemetry into Postgres.
- Building a SQLite maintenance command.

## Success Criteria

- Contract script fails before cleanup runbook exists.
- Contract script passes after runbook creation.
- Runbook explicitly says not to import old SQLite telemetry into Postgres.
