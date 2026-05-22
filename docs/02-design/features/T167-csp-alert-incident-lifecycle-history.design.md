# T167 CSP Alert Incident Lifecycle History Design

Date: 2026-05-22

## Design

T167 adds a small incident event store beside the existing CSP alert acknowledgement store.

## Event Shape

- `fingerprint`: active alert fingerprint.
- `eventType`: `acknowledged`, `snoozed`, `assigned`, or `status_changed`.
- `status`: latest acknowledgement status after the event.
- `actor`: operator principal or access method.
- `assignedTo`: operator assignment for this slice, defaulting to the actor.
- `reason`: sanitized operator reason.
- `occurredAt`: event timestamp.
- `snoozeUntil`: optional snooze expiry.

The event intentionally excludes raw CSP reports, blocked URIs, user agents, and threshold reason text.

## Storage

- In-memory store keeps bounded recent events for local/dev operation.
- PostgreSQL store uses `csp_alert_incident_events`.
- Indexes cover `(fingerprint, occurred_at DESC, id DESC)` for current alert lookup and `(occurred_at DESC, id DESC)` for recent global export/review.
- Default selection follows existing CSP alert central database environment variables and adds `NUXT_CSP_ALERT_INCIDENT_POSTGRES_URL`.

## API And UI

- `acknowledgeCspAlert` accepts an optional incident store and appends `acknowledged` or `snoozed` after the latest-state acknowledgement is saved.
- `buildCspTelemetryDashboard` includes `alertIncidentHistory`.
- `GET /api/security/csp-telemetry` passes the default incident store, keeping the history behind the existing dashboard guard.
- `/security` renders a compact incident lifecycle panel in the same dashboard grid.
