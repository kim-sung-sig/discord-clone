# T127 CSP Alert Acknowledgement Workflow Analysis

Date: 2026-05-20
PDCA Phase: Analysis
Slice: T127 CSP Alert Acknowledgement Workflow

## Findings

- CSP alert persistence from T126 made alert transitions reviewable, but the active banner still lacked operator workflow state.
- A fingerprint derived from aggregate alert reasons is enough to bind acknowledgement to the currently active threshold condition without storing raw CSP report data.
- Acknowledgement should not accept stale or missing fingerprints because a dashboard tab can become outdated while CSP traffic changes.
- Snooze must be bounded so an operator cannot accidentally hide an alert for an unbounded period.

## Risk Review

| Risk | Control |
| --- | --- |
| Stale dashboard acknowledges a different alert | Server recomputes fingerprint and rejects mismatch with 409. |
| Blank or arbitrary reason weakens audit value | Reason is required, whitespace-normalized, and capped. |
| Long snooze hides a real incident | Snooze is bounded to 1-1440 minutes. |
| Operator token leaks to UI | Token remains in session storage/header only; tests assert it is not rendered. |
| Raw telemetry leaks into acknowledgement store | Store records fingerprint and sanitized metadata only. |

## Remaining Gaps

- Acknowledgement is current-state workflow, not a full incident case model.
- There is no acknowledgement history export yet.
- There is no notification fanout when an alert changes from snoozed back to active.
