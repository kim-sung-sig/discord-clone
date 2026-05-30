# T136 CSP Telemetry Postgres Health Metric Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T136 CSP Telemetry Postgres Health Metric

## Payload

`/api/security/csp-telemetry` now includes:

```json
{
  "health": {
    "storage": {
      "backend": "postgres",
      "ok": true,
      "writeFailures": 0
    }
  }
}
```

Optional fields:

- `lastWriteFailureAt`
- `lastError`

## Store Behavior

- In-memory store reports `backend: memory`, `ok: true`, and zero write failures.
- Postgres store probes schema/connection through `health()`.
- Postgres `record()` increments write failure counters before rethrowing write errors.
- Error text is sanitized to avoid exposing Postgres URLs or password-like values.

## UI

The `/security` summary strip adds:

- `Telemetry storage`
- `Write failures`

## Security Review

- The UI and payload do not include database URLs, usernames, passwords, raw CSP report bodies, raw subjects, or raw client IPs.
- `lastError` is bounded and redacts Postgres URLs and password assignment patterns.
