# T127 CSP Alert Acknowledgement Workflow Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T127 CSP Alert Acknowledgement Workflow

## Executive Summary

| View | Content |
| --- | --- |
| Problem | CSP alerts were visible and persistent, but operators could not mark a known alert as triaged or temporarily snooze it. |
| Solution | Add fingerprint-based CSP alert acknowledgement with a required reason and optional bounded snooze window. |
| Operator Effect | Operators can separate new CSP spikes from already-reviewed incidents without suppressing telemetry collection. |
| Core Value | CSP alerting becomes actionable incident workflow state instead of a read-only banner. |

## Scope

- Compute a stable fingerprint for active CSP alert reasons.
- Persist alert acknowledgement state in memory or Postgres.
- Add a guarded POST endpoint for acknowledging the currently active alert.
- Render acknowledgement status, reason, and snooze controls in `/security`.
- Verify validation, UI behavior, and build compatibility.

## Out of Scope

- Multi-event incident timeline and assignment workflow.
- Notification delivery to Slack/Discord/email.
- Raw CSP report storage, raw IP storage, or token exposure.

## Success Criteria

- Acknowledgement requires the current alert fingerprint.
- Reason is required and sanitized.
- Snooze is optional and bounded to 1-1440 minutes.
- Dashboard returns `unacknowledged`, `acknowledged`, or `snoozed` state.
- Operator token is used only as an auth header and never rendered in UI.
