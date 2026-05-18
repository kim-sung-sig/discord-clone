# T51 Durable CSP Telemetry Store Design

Date: 2026-05-18
Slice: T51 Durable CSP Telemetry Store

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Design

The telemetry store lives under `apps/web/server/utils` because the existing CSP route normalization is server-local.

Types:

- `StoredCspTelemetry`: normalized report plus `receivedAt`.
- `CspTelemetrySummary`: total count and counts by directive.
- `CspTelemetryStore`: `record`, `recent`, and `summary`.
- `InMemoryCspTelemetryStore`: bounded in-memory implementation.

The handler remains responsible for content-type and size acceptance. It only calls `store.record()` after `normalizeCspReportPayload` returns an accepted `NormalizedCspReport`.

Routes use `defaultCspTelemetryStore`, which is intentionally an implementation detail for local/dev runtime. A later backend or database-backed implementation can replace the interface without changing normalization tests.

## Privacy

The store never accepts raw request body. It stores only normalized origins, directives, disposition, request id, user-agent hash, and receive time. The existing normalization strips URL query strings and script samples before storage.
