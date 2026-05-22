# T167 CSP Alert Incident Lifecycle History Plan

Date: 2026-05-22
Status: Implemented

## Goal

Keep exportable CSP alert incident history beyond the latest acknowledgement state. The history must support operator review of acknowledgement, snooze, assignment, and future status-change events without storing raw CSP report payloads or threshold reason text as event identity.

## Scope

- Add an incident event model for CSP alert lifecycle changes.
- Persist recent events in memory and PostgreSQL with bounded retention.
- Append an event when an operator acknowledges or snoozes an active CSP alert.
- Expose recent incident history through the guarded CSP telemetry dashboard payload.
- Render the history on `/security` in the existing operations dashboard layout.

## Out Of Scope

- Full assignment mutation UI.
- File export generation.
- External queueing or async event ingestion.
- Production monitoring rule deployment.

## Verification

- `npm.cmd test --workspace @discord-clone/web -- security-headers.test.ts -t "CSP alert incident"` failed before implementation.
- `npm.cmd test --workspace @discord-clone/web -- security-dashboard.test.ts -t "incident lifecycle"` failed before implementation.
- Related web tests and build must pass after implementation.
