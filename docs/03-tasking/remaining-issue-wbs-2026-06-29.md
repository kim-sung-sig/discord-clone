# 남은 작업 및 개선 WBS

작성일: 2026-06-29
범위: 현재 repository 문서, project wiki roadmap, 기존 backlog 기준의 다음 실행 issue 정리

## 결론

다음 작업은 제품 기능 확장보다 `보안/CI/runtime 검증`, `API 계약 정합성`, `권한/감사 통합`, `업로드/접근성 품질`을 먼저 닫는 순서가 맞다. 현재 문서상 T171-C/D/E는 사용자 결정 전 구현 금지이고, T190은 보안 gate runtime이 정책 승인 환경에서 재검증되어야 한다.

## 사용 근거

- Wiki: `index.md`, `wiki/Current Roadmap And Risks.md`, `wiki/Agent Development Guide.md`
- Repo: `docs/03-tasking/improvement-task-backlog.md`
- Repo: `docs/03-tasking/post-t106-residual-task-priority.md`
- Repo: `docs/01-plan/features/T171-security-architecture-decision-prep.plan.md`
- Repo: `docs/01-plan/features/T190-reenable-tracked-ci-operations-gates.plan.md`
- Repo: `docs/04-report/T190-reenable-tracked-ci-operations-gates.report.md`
- Repo: `docs/03-tasking/T172-message-publish-usecase-task-packet.md`

## Issue 목록

| Issue | 우선순위 | 구분 | 제목 | 근거 | 완료 조건 |
| --- | --- | --- | --- | --- | --- |
| IW-01 | P0 | Decision | T171-C MSA 도입 여부 결정 | T171 계획서가 사용자 결정 전 구현 금지로 명시 | 모듈러 모놀리스 유지/부분 분리/전면 MSA 중 하나를 ADR로 승인 |
| IW-02 | P0 | Decision | T171-D 이벤트 드리븐 아키텍처 도입 범위 결정 | Kafka/Gateway/audit/notification 확장 후보가 있으나 표준 이벤트 범위 미정 | 1차 표준 이벤트, schema owner, ordering/idempotency 정책 확정 |
| IW-03 | P0 | Decision | T171-E SAGA 도입 여부 결정 | 다중 리소스 흐름은 후보만 있고 실제 도입 승인 없음 | SAGA 후보 use case와 도입/보류 기준 확정 |
| IW-04 | P0 | Security/CI | T190 보안 gate full runtime 재검증 | report상 npm audit/OSV 외부 전송 정책 차단으로 waiver 기록 | 승인된 환경에서 `qa/security-gate.ps1` full run PASS 또는 명시 waiver 갱신 |
| IW-05 | P0 | Contract | OpenAPI 및 frontend API path drift 해소 | roadmap: `discord-api.ts` 일부 path가 generated `ApiPath` 밖에 있음 | `npm run openapi:check` PASS, 수동 path 예외 목록 0 또는 문서화 |
| IW-06 | P0 | Runtime QA | real backend/browser/runtime gate 안정화 | roadmap: Nuxt/Playwright shutdown, runtime gate 반복성 리스크 존재 | real backend e2e가 고립 port와 cleanup으로 반복 PASS |
| IW-07 | P1 | Security | upload security/content safety 구체화 | backlog T46, T09/T19 follow-up | file type/size/key/preview boundary test와 API/UI 거절 UX 완료 |
| IW-08 | P1 | Auth/Admin | admin permission mutation backend integration | roadmap T45 follow-up: backend permission mutation, hierarchy guard, audit API integration 남음 | role hierarchy guard, mutating API authz, audit 기록, admin UI 연동 PASS |
| IW-09 | P1 | Product | notification mention inbox REST/persistence/UI | roadmap T43: domain behavior 후 REST/persistence/UI follow-up | mention/unread persistence, inbox API, web UI, browser notification smoke |
| IW-10 | P1 | Product/Safety | message search 및 moderation report 통합 | roadmap T44: backend behavior 후 REST/OpenAPI/UI integration follow-up | search/report API, OpenAPI, moderation UI, authz test 완료 |
| IW-11 | P1 | Realtime | Gateway own-source skip observability | post-T106 T187 follow-up | skipped-own-record counter/metric 노출, Redis gateway smoke 보강 |
| IW-12 | P2 | QA Polish | Playwright color warning cleanup | post-T106 T188 follow-up | real-backend Playwright log에서 color env warning 제거 |
| IW-13 | P2 | UX Quality | accessibility/responsive UX pass | backlog T47, T166 이후 품질 hardening | keyboard/focus/mobile overflow guard와 핵심 화면 smoke PASS |
| IW-14 | P2 | Product | bot/webhook skeleton | backlog T48 | webhook create/receive skeleton, auth/audit 최소 test |
| IW-15 | P2 | Product | server events/scheduling skeleton | backlog T49 | event schedule model/API/UI skeleton과 permission test |
| IW-16 | P0 | Stability/Architecture | shared circuit breaker foundation | 사용자 요청: pod 증설/외부 연동/이벤트 half-open 과부하 방지 | shared common circuit breaker, half-open probe cap, focused backend check PASS |
| IW-17 | P0 | Architecture/Message | message CQRS read-model boundary | 사용자 요청: 메시지 시스템 고도화, 대용량 트래픽, CQRS | message list/search가 query service/read model port를 사용하고 PostgreSQL projection query가 aggregate edit history 로딩을 피함 |

## WBS

| WBS | 선행 | 산출물 | 세부 작업 | 검증 |
| --- | --- | --- | --- | --- |
| 1.0 결정 잠금 | 없음 | T171-C/D/E ADR | MSA, event-driven, SAGA 선택지 정리 및 사용자 승인 | ADR 승인 여부 확인 |
| 2.0 보안 gate 복구 | 1.0 병행 가능 | T190 runtime evidence | 승인 환경에서 security full gate 실행, waiver 필요 시 사유 갱신 | `qa/security-gate.ps1`, `qa/security-gate.contract.ps1` |
| 3.0 API 계약 정리 | 1.0 | OpenAPI/client drift report | `discord-api.ts` 수동 path 식별, generated `ApiPath` 편입 또는 예외 문서화 | `npm run openapi:check`, web tests |
| 4.0 runtime QA 안정화 | 2.0, 3.0 | 반복 가능한 real backend e2e | port isolation, process cleanup, backend/browser smoke 안정화 | `qa/real-backend-e2e.ps1` |
| 5.0 권한/감사 통합 | 3.0 | Admin permission integration | role mutation backend authz, hierarchy guard, audit API, UI 연결 | backend authz tests, web component/e2e |
| 6.0 upload safety | 3.0 | Upload security slice | validation, object key safety, unsafe preview boundary, UI error path | backend storage tests, web upload tests |
| 7.0 product integration A | 3.0, 4.0 | Notification inbox | REST/persistence/UI/browser notification 연결 | backend tests, OpenAPI check, web e2e |
| 8.0 product integration B | 3.0, 4.0 | Search/report workflow | search/report API, moderation report UI, permission/audit checks | backend tests, OpenAPI check, web e2e |
| 9.0 realtime observability | 4.0 | Gateway skip metrics | own-source skip counter와 dashboard/log/metric 노출 | Redis gateway smoke |
| 10.0 UX hardening | 4.0 | Accessibility/responsive pass | keyboard/focus/mobile overflow/accessibility smoke | Playwright layout/a11y smoke |
| 11.0 stability foundation | 4.0 이후 | Circuit breaker primitive | event adapter, 외부 integration, SAGA process-manager가 재사용할 half-open probe 제한 기반 | `cd backend && ./gradlew :backend:shared:common:check` |
| 12.0 message CQRS foundation | 11.0 병행 가능 | Message read model query boundary | list/search controller를 query service로 이동, read-model port, PostgreSQL projection SQL, 다음 projection table/projector 확장점 마련 | message module test, boot controller/JDBC focused tests |
| 13.0 deferred product skeletons | 5.0 이후 | Bot/webhook, server events | 최소 domain/API/UI skeleton | focused backend/web tests |

## 권장 실행 순서

1. IW-01~IW-03: 결정이 필요한 architecture 항목부터 닫는다.
2. IW-04~IW-06: CI/runtime/API 계약을 먼저 안정화한다.
3. IW-08, IW-07: 권한/감사와 업로드 안전성처럼 보안 blast radius가 큰 기능을 처리한다.
4. IW-09~IW-10: notification/search/report 제품 통합을 진행한다.
5. IW-11~IW-13: 관측성, QA log, 접근성/반응형 품질을 정리한다.
6. IW-16: shared 안정성 primitive를 event/outbox/external integration에 적용한다.
7. IW-17: 메시지 list/search를 CQRS read-model 기반으로 분리하고 projection table/projector로 확장한다.
8. IW-14~IW-15: bot/webhook, server events skeleton을 시작한다.

## 제외한 것

- T172 메시지 발행 리뉴얼은 task packet상 GREEN 기록과 PR readiness report가 있어 신규 잔여 issue로 올리지 않았다.
- T170은 roadmap상 완료된 operator token audit review UI로 보아 제외했다.
- 기존 완료 task 목록은 다시 복사하지 않았다. 이 문서는 다음 실행 후보만 다룬다.

## 진행 기록

### 2026-06-29 IW-09 frontend-local slice

- 구현: web shell activity bar에 Inbox view를 추가해 current-user mention, unread channel, unread DM을 한 화면에서 확인한다.
- 구현: channel inbox row 클릭 시 기존 `selectChannel`/`markChannelRead` 흐름으로 이동하고 unread badge를 제거한다.
- 보류: REST, persistence, browser notification permission flow는 다음 IW-09 backend/API slice로 남긴다.
- 검증: `npm run test -w apps/web -- app-shell.test.ts` PASS.
- 검증: `npm run e2e -w apps/web -- app-shell.spec.ts --grep "notification inbox"` PASS.

### 2026-06-30 IW-10 frontend-local slice

- 구현: Search workbench view에서 local message body 검색 결과를 표시한다.
- 구현: 검색 결과에서 메시지를 신고하고 open report queue에서 처리 완료할 수 있게 했다.
- 구현: report 생성/처리는 기존 moderation audit log에 `MESSAGE_REPORTED`, `MESSAGE_REPORT_RESOLVED`를 남긴다.
- 보류: REST, OpenAPI, persistence, moderator authorization은 다음 IW-10 backend/API slice로 남긴다.
- 검증: `npm run test -w apps/web -- app-shell.test.ts` PASS.
- 검증: `npm run e2e -w apps/web -- app-shell.spec.ts --grep "searches messages"` PASS.

### 2026-06-30 IW-10 backend report API slice

- 구현: 기존 `MessageReport` 도메인을 HTTP로 연결해 message report 생성, pending report 조회, report resolve API를 추가했다.
- 구현: 신고 생성은 bearer user, channel view permission, 실제 message 존재를 확인한다.
- 구현: pending report 조회와 resolve는 manage messages 권한으로 제한한다.
- 보류: PostgreSQL persistence, OpenAPI 생성물, web client real API integration은 다음 slice로 남긴다.
- RED: `cd backend && ./gradlew :backend:boot:test --tests com.example.discord.moderation.ModerationControllerTest.memberCanReportMessageAndModeratorCanResolveIt`가 endpoint 미존재로 fail.
- GREEN: `cd backend && ./gradlew :backend:boot:test --tests com.example.discord.moderation.ModerationControllerTest.memberCanReportMessageAndModeratorCanResolveIt` PASS.
- 검증: `cd backend && ./gradlew :backend:boot:test --tests com.example.discord.moderation.ModerationControllerTest` PASS.
- 검증: `cd backend && ./gradlew :backend:modules:moderation:test` PASS.

### 2026-06-30 IW-10 web report API integration

- 구현: `discordApiPaths.guild`에 message report 생성, pending 조회, resolve REST path를 추가했다.
- 구현: Search workbench report/resolve 버튼이 auth token이 있을 때 backend report API를 호출하고, backend가 없으면 기존 local queue fallback을 유지한다.
- 구현: backend response를 shell moderation report queue로 매핑한다.
- 보류: OpenAPI generated client drift 정리와 PostgreSQL-backed report persistence는 다음 slice로 남긴다.
- 검증: `npm run test -w apps/web -- app-shell.test.ts shell-contracts.test.ts` PASS.
- 검증: `npm run e2e -w apps/web -- app-shell.spec.ts --grep "searches messages"` PASS.
- 검증: `npm run build -w apps/web` PASS.
- 참고: `npm run lint -w apps/web`는 workspace에 `lint` script가 없어 실행 불가.

### 2026-06-30 IW-10 OpenAPI report contract slice

- 구현: `qa/openapi-contract.mjs`에 message report 생성, pending 조회, resolve endpoints와 request schemas를 추가했다.
- 구현: `docs/api/openapi.json`와 `packages/api-client/src/generated/openapi-types.ts`를 재생성해 report paths를 generated `ApiPath`에 포함했다.
- 구현: `qa/openapi-contract.test.mjs`에 report endpoint/schema guard를 추가했다.
- 검증: `npm run openapi:check` PASS.
- 미검증: 추가 `@discord-clone/api-client` package test는 도구 사용 한도 승인 거절로 실행하지 못했다.

### 2026-06-30 IW-16 backend stability foundation

- 구현: `backend/shared/common`에 재사용 가능한 `CircuitBreaker`를 추가했다.
- 구현: CLOSED/OPEN/HALF_OPEN 상태, open duration, half-open concurrent probe cap, half-open success threshold, stale half-open permit generation guard를 지원한다.
- 목적: Gateway/event adapter, message outbox relay, 외부 integration, 미래 SAGA process-manager가 half-open 상태에서 과도한 probe traffic을 내지 않도록 하는 공통 기반이다.
- RED: `cd backend && ./gradlew :backend:shared:common:test --tests com.example.discord.common.CircuitBreakerTest`가 `CircuitBreaker` 미존재로 compile fail.
- GREEN: `cd backend && ./gradlew :backend:shared:common:test --tests com.example.discord.common.CircuitBreakerTest` PASS.
- 검증: `cd backend && ./gradlew :backend:shared:common:test` PASS.
- 검증: `cd backend && ./gradlew :backend:shared:common:check` PASS.

### 2026-06-30 IW-17 message CQRS read-model boundary

- 구현: 메시지 list/search 전용 `ChannelMessageQueryService`와 `ChannelMessageReadModelPort`를 추가해 write use case와 query path를 분리했다.
- 구현: `MessageController`의 list/search를 aggregate reader/search port에서 query service 기반 read-model 응답으로 이동했다.
- 구현: in-memory adapter는 기존 aggregate를 read model로 변환하고, PostgreSQL adapter는 message projection SQL로 edit history aggregate 로딩을 피한다.
- 보류: 물리적 projection table, outbox projector, cross-system SAGA/process-manager integration은 다음 backend slice로 남긴다.
- RED: `cd backend && ./gradlew :backend:modules:message:test --tests com.example.discord.message.DefaultChannelMessageQueryServiceTest`가 CQRS 타입 미존재로 compile fail.
- GREEN: `cd backend && ./gradlew :backend:modules:message:test --tests com.example.discord.message.DefaultChannelMessageQueryServiceTest` PASS.
- 검증: `cd backend && ./gradlew :backend:boot:test --tests com.example.discord.message.MessageControllerTest` PASS.
- 검증: `cd backend && ./gradlew :backend:boot:test --tests com.example.discord.message.JdbcMessageStoreTest` PASS.

### 2026-06-30 IW-17 backend physical read projection

- 구현: `V11__message_read_projection.sql`로 `message_read_projection` 물리 테이블과 channel cursor/search index를 추가했다.
- 구현: `JdbcMessageStore`의 message save/savePublished transaction에서 projection row를 동기 upsert한다.
- 구현: PostgreSQL read-model list/search가 `messages` aggregate table 대신 `message_read_projection`을 조회한다.
- 보류: pg_trgm/full-text search, 비동기 projector, cross-system SAGA/process-manager는 검색 semantics와 운영 경계가 커져 다음 slice로 남긴다.
- 검증: `cd backend && ./gradlew :backend:boot:test --tests com.example.discord.message.JdbcMessageStoreTest --tests com.example.discord.persistence.PersistenceBootstrapTest` PASS.
- 검증: `cd backend && ./gradlew :backend:modules:message:test --tests com.example.discord.message.DefaultChannelMessageQueryServiceTest` PASS.
