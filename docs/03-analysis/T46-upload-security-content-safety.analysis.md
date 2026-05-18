# T46 Upload Security & Content Safety Analysis

Date: 2026-05-18
Slice: T46 Upload Security & Content Safety

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 완료 > 기준점 통과 > 다음 계획 진행 가능

## Scope Reviewed

T46 implemented the first backend upload security slice:

- Request-time filename extension and declared content-type consistency.
- Upload-completion file signature validation for PNG and JPEG.
- Malware scanner provider interface skeleton.
- Explicit fail-closed scanner behavior for unavailable and blocked results.
- Existing server-generated object key and orphan cleanup behavior preserved.

## TDD Evidence

RED:

- `:backend:modules:storage:test --tests com.example.discord.storage.InMemoryAttachmentServiceTest` failed because secure `markUploaded(..., bytes)`, scanner constructor, and `AttachmentScanResult` did not exist.

GREEN:

- Added `FileSignatureValidator`, `AttachmentScanner`, `AttachmentScanResult`, and `AttachmentScanStatus`.
- Added filename/content-type validation to `AttachmentUploadPolicy`.
- Added secure `markUploaded(attachmentId, ownerId, bytes)` validation path.
- Re-ran storage tests successfully.

Review verification:

- `.\gradlew.bat --no-daemon :backend:modules:storage:test :backend:boot:compileJava`
- Result: PASS

## Six-Metric Review

| Metric | Score | Notes |
| --- | ---: | --- |
| Plan/Design Alignment | 5 | Implemented the documented first backend slice: mismatch rejection, signature validation, scanner skeleton. |
| TDD Evidence | 5 | RED compile failures were observed before implementation; GREEN and review verification passed. |
| Security/Privacy | 4 | Signature mismatch and scanner unavailable now fail closed. Legacy `markUploaded(id, owner)` remains for compatibility and must not be used by production API. |
| Integration Compatibility | 4 | Existing storage tests and boot compile pass. Constructor compatibility is preserved. |
| Documentation/Traceability | 5 | Plan/design/analysis/report/feedback docs record the loop, score, and residual risks. |
| Residual Risk Control | 4 | Real scanner, preview SSRF protection, API routing to secure method, and production object-store callbacks are explicit follow-ups. |

Total: 27/30

Decision: PASS

## Residual Risks

| Risk | Impact | Follow-up |
| --- | --- | --- |
| Legacy upload completion bypass remains | Production API could call non-byte validating method if not migrated | T80 route upload completion through secure markUploaded bytes path |
| No real scanner provider | Scanner interface exists but no ClamAV/vendor integration | T81 malware scanner provider integration |
| No preview SSRF boundary yet | Future remote preview fetch could target internal endpoints | T82 preview URL SSRF guard and safe fetch policy |
| Signature support limited to PNG/JPEG | More file types need explicit signatures before allowlist expansion | T83 file signature registry expansion |

## Decision

T46 passes as a backend upload security foundation. Continue to T47 if following the T30-T49 breadth sequence, or deepen T46 with API migration and preview SSRF guard.
