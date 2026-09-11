---
slug: T137-csp-telemetry-sqlite-legacy-cleanup-note
ticket: T137
phase: feedback
hub: "[[T137-csp-telemetry-sqlite-legacy-cleanup-note]]"
---
> 🧭 [[T137-csp-telemetry-sqlite-legacy-cleanup-note]] · PDCA: [[T137-csp-telemetry-sqlite-legacy-cleanup-note.plan]] → [[T137-csp-telemetry-sqlite-legacy-cleanup-note.design]] → [[T137-csp-telemetry-sqlite-legacy-cleanup-note.analysis]] → [[T137-csp-telemetry-sqlite-legacy-cleanup-note.report]] → **feedback**

# T137 CSP Telemetry SQLite Legacy Cleanup Note Feedback

Date: 2026-05-20
Slice: T137 CSP Telemetry SQLite Legacy Cleanup Note

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T110 Node SQLite runtime compatibility gate | P2 | Now legacy-only, but old SQLite references still exist in historical docs. |
| T115 SQLite telemetry maintenance command | P2 | Now legacy-only; only needed if a deployment deliberately restores SQLite telemetry. |

## Notes

- T137 closes the legacy SQLite archive/delete guidance gap after Postgres migration.
