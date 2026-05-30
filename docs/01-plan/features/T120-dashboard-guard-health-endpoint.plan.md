# T120 Dashboard Guard Health Endpoint Plan

Date: 2026-05-19
Slice: T120 Dashboard Guard Health Endpoint

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T113 made the browser security dashboard fail closed when guard configuration is required but missing. T120 adds a secret-safe health endpoint so operators can inspect whether the dashboard guard is configured, required, ready, locally open, or fail-closed.

## Implementation Plan

Major topics:

1. Guard health model
   - Summarize configured guard methods as booleans only.
   - Avoid returning configured token, JWT secret, URLs with secrets, or principal allowlist values.

2. API endpoint
   - Add `/api/security/dashboard-guard-health`.
   - Return `503` when guard configuration is required but absent.
   - Return health payload otherwise.

3. Test coverage
   - Add RED/GREEN tests for production fail-closed and configured ready states.
   - Assert secrets do not appear in serialized payloads.

## Out of Scope

- Dashboard UI rendering for the guard health endpoint.
- Authenticated backend guard self-test request.
- Alerting on guard health changes.

## Acceptance Criteria

- Guard health payload exposes status and method booleans.
- Production unconfigured guard reports `fail-closed`.
- Configured guard reports `ready`.
- Serialized payload does not contain token or JWT secret values.
- Focused access tests, web tests, and build pass.
