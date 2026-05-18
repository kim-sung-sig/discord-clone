# T42 OpenAPI & Frontend Client Contract Feedback

작성일: 2026-05-18  
Slice: T42 OpenAPI & Frontend Client Contract

## Feedback Items

| ID | Severity | Finding | Follow-up |
| --- | --- | --- | --- |
| T42-FB-001 | High | OpenAPI artifact is deterministic but not extracted from Spring controller metadata. | T64 Spring runtime OpenAPI extraction or controller-contract comparison. |
| T42-FB-002 | High | `openapi.json` covers representative public APIs, not every implemented REST endpoint. | T65 full REST endpoint coverage pass. |
| T42-FB-003 | Medium | Web stores still use the old local REST client instead of the shared `@discord-clone/api-client`. | T66 migrate web REST calls to shared API client. |
| T42-FB-004 | High | Backend controllers do not yet all return the standard `ApiErrorResponse` envelope. | T67 standard backend error envelope migration. |
