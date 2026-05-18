# T46 Upload Security & Content Safety Report

Date: 2026-05-18
Slice: T46 Upload Security & Content Safety

## Summary

T46 added the first upload safety layer to the storage module. Upload requests now reject common image filename/content-type mismatches, uploaded bytes can be validated against PNG/JPEG signatures before marking an attachment uploaded, and malware scanner provider behavior is explicit and fail-closed.

## Loop Result

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 완료 > 27/30 PASS > 다음 계획 진행 가능

## Implemented Changes

- Added `FileSignatureValidator`.
- Added `AttachmentScanner`, `AttachmentScanResult`, and `AttachmentScanStatus`.
- Added secure `markUploaded(attachmentId, ownerId, bytes)` path.
- Added scanner unavailable and scanner blocked fail-closed behavior.
- Added filename extension/content-type mismatch validation.
- Updated storage tests for signature mismatch and scanner unavailable behavior.
- Added T46 Plan, Design, Analysis, Report, and Feedback docs.

## Verification

Passed:

```powershell
.\gradlew.bat --no-daemon :backend:modules:storage:test --tests com.example.discord.storage.InMemoryAttachmentServiceTest
.\gradlew.bat --no-daemon :backend:modules:storage:test :backend:boot:compileJava
```

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

## Next Recommendation

Proceed to T47 Accessibility & Responsive UX Pass for breadth. If T46 is deepened first, route the production upload-completion API through the secure byte-validating method and add preview SSRF protection.
