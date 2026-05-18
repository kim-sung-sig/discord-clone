# T51 Durable CSP Telemetry Store Report

Date: 2026-05-18
Slice: T51 Durable CSP Telemetry Store

## Summary

T51 added a CSP telemetry store boundary and local in-memory implementation. Accepted CSP reports are now stored after normalization, rejected reports are ignored, and operators can query recent sanitized telemetry or summary counts by effective directive.

## Loop Result

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 완료 > 27/30 PASS > 다음 계획 진행 가능

## Implemented Changes

- Added `CspTelemetryStore`.
- Added `InMemoryCspTelemetryStore`.
- Added `defaultCspTelemetryStore`.
- Added handler option for telemetry recording.
- Wired enforce and report-only CSP routes to the shared store.
- Added tests for accepted persistence, rejected non-persistence, summary counts, and sensitive payload exclusion.

## Verification

Passed:

```powershell
npm test -w apps/web -- security-headers.test.ts
npm run build -w apps/web
npm test -w apps/web -- security-headers.test.ts shell-contracts.test.ts
```

Note: Running Vitest concurrently with `nuxt build` can produce a transient `#app-manifest` alias error while Nuxt cache files are regenerated. The tests passed when re-run after build completion.

## Six-Metric Review Score

| Metric | Score |
| --- | ---: |
| Plan/Design Alignment | 5/5 |
| TDD Evidence | 5/5 |
| Security/Privacy | 4/5 |
| Integration Compatibility | 4/5 |
| Documentation/Traceability | 5/5 |
| Residual Risk Control | 4/5 |

Total: 27/30

Decision: PASS
