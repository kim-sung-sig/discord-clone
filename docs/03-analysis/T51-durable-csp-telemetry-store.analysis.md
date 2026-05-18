# T51 Durable CSP Telemetry Store Analysis

Date: 2026-05-18
Slice: T51 Durable CSP Telemetry Store

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 완료 > 기준점 통과 > 다음 계획 진행 가능

## Scope Reviewed

T51 added a durable-store boundary for sanitized CSP telemetry:

- `CspTelemetryStore` interface.
- `InMemoryCspTelemetryStore` bounded implementation.
- Accepted CSP reports are recorded after normalization.
- Rejected reports do not affect telemetry.
- Summary counts by effective directive are available.
- Routes use a shared local telemetry store.

## TDD Evidence

RED:

- `npm test -w apps/web -- security-headers.test.ts` failed because `csp-telemetry-store` did not exist.

GREEN:

- Added `apps/web/server/utils/csp-telemetry-store.ts`.
- Added `telemetryStore` and deterministic `now` options to `handleCspReportPayload`.
- Wired CSP routes to the default telemetry store.
- Re-ran T51 tests successfully.

Review verification:

- `npm test -w apps/web -- security-headers.test.ts`: PASS
- `npm run build -w apps/web`: PASS
- `npm test -w apps/web -- security-headers.test.ts shell-contracts.test.ts`: PASS after build completed

## Six-Metric Review

| Metric | Score | Notes |
| --- | ---: | --- |
| Plan/Design Alignment | 5 | Implemented store boundary, in-memory implementation, handler integration, and directive summary. |
| TDD Evidence | 5 | RED failure was observed before implementation; GREEN and review verification passed. |
| Security/Privacy | 4 | Store only receives normalized reports, not raw payloads. Persistence is in-memory only and needs production backing. |
| Integration Compatibility | 4 | Existing CSP handler contract is backward-compatible and routes still return 204. |
| Documentation/Traceability | 5 | Plan/design/analysis/report/feedback docs added. |
| Residual Risk Control | 4 | Database persistence, retention policy, dashboard, and alerting are explicit follow-ups. |

Total: 27/30

Decision: PASS

## Residual Risks

| Risk | Impact | Follow-up |
| --- | --- | --- |
| Store is in-memory | Telemetry is lost on process restart | T98 database-backed CSP telemetry store |
| No retention policy controls | Long-running servers need bounded retention by time and count | T99 CSP telemetry retention policy |
| No dashboard endpoint/UI | Operators cannot inspect trends through UI yet | T54 browser security dashboard |
| No alerting | Spikes in CSP violations do not notify operators | T100 CSP telemetry alert threshold |
