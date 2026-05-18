# T46 Upload Security & Content Safety Feedback

Date: 2026-05-18
Slice: T46 Upload Security & Content Safety

## Feedback Items

| Id | Priority | Observation | Proposed Task |
| --- | --- | --- | --- |
| T46-FB-001 | High | Legacy `markUploaded(attachmentId, ownerId)` remains for compatibility and bypasses byte signature validation. | T80 route upload completion through secure markUploaded bytes path. |
| T46-FB-002 | High | Scanner interface exists but no real provider is wired. | T81 malware scanner provider integration. |
| T46-FB-003 | High | Preview/fetch SSRF boundary is not implemented in this slice. | T82 preview URL SSRF guard and safe fetch policy. |
| T46-FB-004 | Medium | Signature support is limited to PNG/JPEG. | T83 file signature registry expansion. |
| T46-FB-005 | Medium | Scanner blocked result does not yet create security/audit telemetry. | Add upload security audit events after storage API integration. |

## Loop Decision

T46 scored 27/30 and passed the threshold. Continue to T47 unless upload API migration is prioritized first.
