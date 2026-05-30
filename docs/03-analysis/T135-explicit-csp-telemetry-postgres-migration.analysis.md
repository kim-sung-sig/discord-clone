# T135 Explicit CSP Telemetry Postgres Migration Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T135 Explicit CSP Telemetry Postgres Migration

## Findings

| Finding | Result |
| --- | --- |
| Runtime stores already had the required DDL | The explicit SQL migration mirrors the existing lazy schema. |
| T124 and T126 added more tables after T109 | Migration includes `csp_rate_limit_telemetry` and `csp_alert_transitions`, not only `csp_telemetry`. |
| Local central Postgres already had the tables | Applying the migration succeeded with `IF NOT EXISTS` notices. |
| Runtime compatibility still matters | Defensive lazy creation remains in stores for local/test resilience. |

## Security Review

The migration does not introduce raw client IP storage or unsanitized CSP report bodies. It preserves the current privacy posture: sanitized origins/directives, hashed user-agent/subject fields, retention counters, and aggregate alert reasons.

## Residual Risk

- Operators still need telemetry DB health and write-failure visibility; tracked as T136.
- Legacy SQLite cleanup guidance remains tracked as T137.
