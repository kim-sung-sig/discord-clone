# T42 OpenAPI & Frontend Client Contract Plan

작성일: 2026-05-17  
PDCA Phase: Plan  
Slice: T42 OpenAPI & Frontend Client Contract

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T23 이후 frontend는 실제 API를 더 많이 사용하지만, backend response/error shape 변경이 typed client와 CI에서 자동으로 검출되는 구조는 아직 약하다. |
| Solution | OpenAPI spec generation, frontend API client sync, request/error contract validation, CI drift check를 추가한다. |
| Function UX Effect | 사용자는 API 변경으로 인한 UI 실패를 덜 겪고, 에러 메시지/request id/권한 실패가 일관된 형태로 표시된다. |
| Core Value | backend와 multi-platform frontend가 수동 타입 복사 대신 검증 가능한 API 계약을 공유한다. |

## Scope

- Generate or export backend OpenAPI spec for public REST endpoints.
- Include standard error response shape and `X-Request-Id` header policy in the spec.
- Generate or validate frontend API client types from the OpenAPI contract.
- Add CI drift check that fails when backend spec and checked-in/generated client diverge.
- Separate internal/test-only endpoints from public client contract.
- Document API versioning and breaking-change review policy.
- Add contract tests for representative auth, guild, channel, message, voice, stage, and security endpoints.

## Out of Scope

- Full public Discord-compatible API.
- Bot API contract. That belongs to T48.
- GraphQL.
- Automatic SDK publishing to npm.
- Rewriting every existing frontend API call in one pass if staged migration is safer.

## Success Criteria

- Backend OpenAPI spec is generated reproducibly.
- Standard error shape includes request id, safe error code, and safe user-facing message.
- Frontend client contract is generated or validated from the spec.
- CI fails on uncommitted or incompatible API/client drift.
- Internal/admin/test endpoints are explicitly included or excluded by policy.
- T42 analysis/report records first drift findings and migration residual risk.

## Failure Criteria

- Frontend client remains manually typed without contract validation.
- Backend response changes are only found by UI/e2e tests.
- Error shape and request id policy are absent from OpenAPI.
- CI drift check is optional or non-blocking.
- Sensitive/internal endpoints are accidentally exposed as public client contract.
