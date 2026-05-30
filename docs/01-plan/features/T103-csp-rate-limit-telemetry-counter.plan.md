# T103 CSP Rate-limit Telemetry Counter Plan

## Objective

Expose visibility into CSP reports dropped by rate limiting so operators can distinguish "no reports" from "reports are being throttled".

## Current State

- T53/T102 rate-limit CSP reports before parsing.
- Rate-limited reports return `204` and are intentionally not stored as CSP telemetry.
- The security dashboard currently shows only accepted normalized reports.

## Scope

1. Add a small rate-limit telemetry counter store.
2. Record a sanitized counter event when a CSP report is rate limited.
3. Include rate-limit totals in the security dashboard payload.
4. Avoid storing raw IPs or raw report payloads.
5. Keep the first implementation in-memory and bounded.

## Acceptance Criteria

- A rate-limited CSP report increments a limited-report counter.
- Accepted reports do not increment the limited-report counter.
- Dashboard payload includes `rateLimit.limitedTotal`.
- Full web tests and build pass.

