---
slug: T137-csp-telemetry-sqlite-legacy-cleanup-note
ticket: T137
phase: report
hub: "[[T137-csp-telemetry-sqlite-legacy-cleanup-note]]"
---
> 🧭 [[T137-csp-telemetry-sqlite-legacy-cleanup-note]] · PDCA: [[T137-csp-telemetry-sqlite-legacy-cleanup-note.plan]] → [[T137-csp-telemetry-sqlite-legacy-cleanup-note.design]] → [[T137-csp-telemetry-sqlite-legacy-cleanup-note.analysis]] → **report** → [[T137-csp-telemetry-sqlite-legacy-cleanup-note.feedback]]

# T137 CSP Telemetry SQLite Legacy Cleanup Note Report

Date: 2026-05-20
Slice: T137 CSP Telemetry SQLite Legacy Cleanup Note

## Completed

- Added `docs/runbooks/csp-telemetry-sqlite-legacy-cleanup.md`.
- Added `qa/csp-sqlite-legacy-cleanup.contract.ps1`.
- Documented locate, archive, delete, verification, and rollback steps.
- Documented that old SQLite telemetry should not be imported into Postgres.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/csp-sqlite-legacy-cleanup.contract.ps1` failed because the cleanup runbook was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/csp-sqlite-legacy-cleanup.contract.ps1` passed.

## Notes

- This task is documentation-only and does not change runtime telemetry behavior.
