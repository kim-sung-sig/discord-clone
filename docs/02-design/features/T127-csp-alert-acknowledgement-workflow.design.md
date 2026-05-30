# T127 CSP Alert Acknowledgement Workflow Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T127 CSP Alert Acknowledgement Workflow

## Design

Add `csp-alert-acknowledgement-store.ts` with a narrow store interface:

| Method | Purpose |
| --- | --- |
| `acknowledge(command)` | Upsert acknowledgement state for the active alert fingerprint. |
| `current(fingerprint, now)` | Return current status as `unacknowledged`, `acknowledged`, or `snoozed`. |

The default store selects Postgres through `NUXT_CSP_ALERT_ACK_POSTGRES_URL` or `NUXT_CSP_TELEMETRY_POSTGRES_URL`, otherwise it falls back to in-memory storage.

## Dashboard Flow

1. Dashboard builds the aggregate CSP summary.
2. `evaluateCspTelemetryAlert` computes current alert state.
3. `cspAlertFingerprint` hashes sorted alert reasons into a stable 24-character fingerprint.
4. Dashboard reads acknowledgement state for the active fingerprint.
5. `/security` renders status and exposes a reason + snooze form.

## Acknowledgement Flow

1. Operator submits the current fingerprint, reason, and optional snooze minutes.
2. `POST /api/security/csp-alert-ack` authorizes through the existing dashboard guard.
3. The route recomputes the current alert fingerprint server-side.
4. Missing fingerprints return 400; stale fingerprints return 409.
5. Valid requests persist sanitized acknowledgement state.

## Security Review

Acknowledgements store only:

- alert fingerprint
- sanitized reason
- sanitized principal label
- acknowledgement time
- optional snooze expiry

They do not store raw CSP payloads, raw URLs, request headers, IP addresses, user-agent values, or operator tokens.
