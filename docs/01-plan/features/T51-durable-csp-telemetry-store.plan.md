# T51 Durable CSP Telemetry Store Plan

Date: 2026-05-18
Slice: T51 Durable CSP Telemetry Store

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T51 follows T38 feedback. CSP report endpoints already normalize and redact reports, but accepted reports are only logged. This slice adds a durable-store boundary and in-memory implementation so operators can query sanitized telemetry trends without keeping raw report bodies.

## Implementation Plan

Major topics:

1. Store boundary
   - Add `CspTelemetryStore` interface.
   - Store only `NormalizedCspReport` plus server-side timestamps.

2. In-memory durable-style implementation
   - Append accepted reports.
   - Query recent reports.
   - Aggregate counts by effective directive.

3. Handler integration
   - `handleCspReportPayload` records accepted normalized reports through an optional store.
   - Routes share a singleton in-memory store for local runtime visibility.

## Acceptance Criteria

- Accepted CSP report is persisted through the store.
- Rejected/oversized/unsupported reports are not persisted.
- Stored telemetry never includes raw URL query strings, script samples, or request body.
- Summary counts by directive are available.

## Failure Criteria

- Raw report body or sensitive URL query values are stored.
- Rejected report payloads affect telemetry counts.
- Route still only logs and cannot retain accepted telemetry.
