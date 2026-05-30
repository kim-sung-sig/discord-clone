# T124 Distributed CSP Rate-limit Telemetry Analysis

Date: 2026-05-20
Slice: T124 Distributed CSP Rate-limit Telemetry

## Findings

- T103 added `limitedTotal`, but the default store was in-memory only.
- T109 already centralized accepted CSP telemetry in Postgres, so rate-limit telemetry can reuse the same database URL.
- The report handler had an async path for rate limiters and telemetry stores, but limited-report telemetry writes were not awaited.
- The dashboard assumed the rate-limit summary was synchronous.

## Security Review

- Raw rate-limit subjects are still not persisted.
- Postgres storage uses SHA-256 subject hashes only.
- Dashboard output remains aggregate-only: `limitedTotal`.
- A dedicated rate-limit telemetry URL is supported, but the existing central CSP telemetry URL is sufficient for normal deployment.

## Residual Risk

- The new Postgres table is still lazily created by the application. T135 should include `csp_rate_limit_telemetry` in the explicit migration follow-up.
- Per-subject diagnostics remain out of scope and are tracked separately by T128.
