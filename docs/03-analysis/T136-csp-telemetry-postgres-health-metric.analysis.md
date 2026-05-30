# T136 CSP Telemetry Postgres Health Metric Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T136 CSP Telemetry Postgres Health Metric

## Findings

| Finding | Result |
| --- | --- |
| Existing dashboard payload had counts but no storage health | Added `health.storage`. |
| Postgres write failures previously surfaced only as request failures | Postgres store now records aggregate write failure count and last failure time. |
| UI summary strip was fixed at four columns | Changed to responsive auto-fit cards so storage health labels do not overflow. |
| Health errors could leak connection details if copied directly | Added redaction and length bounding. |

## Security Review

The health metric exposes only backend type, boolean readiness, failure count, last failure timestamp, and sanitized error text. It does not expose the configured Postgres URL or password.

## Residual Risk

- Redis CSP limiter lifecycle metrics remain tracked as T131.
- External metrics export is not yet implemented.
