# T53 CSP Report Rate Limiter Plan

Date: 2026-05-18
Slice: T53 CSP Report Rate Limiter

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T38 added CSP report endpoints and sanitized normalization. T51 added durable in-process telemetry storage, and T52 promoted strict style policy enforcement. T53 reduces abuse risk on the CSP report endpoints by applying a per-subject rate limit before normalization or telemetry persistence.

## Implementation Plan

Major topics:

1. Rate limiter foundation
   - Add a small CSP-report-specific limiter interface.
   - Provide a bounded in-memory implementation with window reset behavior.

2. Handler integration
   - Let `handleCspReportPayload` consume a subject before parsing.
   - Return `204` with `accepted: false` for limited reports to avoid reflected response content.

3. Route integration
   - Derive a stable subject from `x-forwarded-for` or `x-real-ip`.
   - Share the default limiter across enforce and report-only routes.

4. Test coverage
   - Verify accepted reports are stored until the limit is reached.
   - Verify limited reports are not normalized or persisted.
   - Verify the window resets after expiry.

## Out of Scope

- Redis-backed distributed limiter for multi-node Nuxt deployments.
- Operator dashboard for limited CSP report rates.
- Dynamic per-tenant policy overrides.

## Acceptance Criteria

- CSP report handler supports a rate limiter option.
- Same-subject reports over the limit return `204` and `accepted: false`.
- Limited reports do not enter telemetry.
- Enforce and report-only routes pass a rate limit subject.
- Focused tests and web build pass.

## Failure Criteria

- Limited reports are stored in telemetry.
- CSP report endpoints return reflected error bodies.
- Build or focused CSP tests fail.
