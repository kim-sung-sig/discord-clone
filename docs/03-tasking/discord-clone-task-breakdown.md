# Discord Clone Task Breakdown

작성일: 2026-05-13  
기준: 기능 분석 보고서 + 엔터프라이즈 설계

## 1. 맨먼스 산정

전제:

- 1 MM = 숙련 엔지니어 1명이 1개월 집중 작업
- 테스트/QA/문서화 포함
- 인프라 운영 자동화는 로컬/CI 기준, 프로덕션 SRE는 별도

| Phase | 범위 | 예상 MM |
| --- | --- | ---: |
| Phase 0 | repo/bootstrap/test harness/CI/local infra | 1.5 |
| Phase 1 | auth/user/profile/session | 2.0 |
| Phase 2 | guild/channel/role/permission/invite | 5.0 |
| Phase 3 | message/gateway/basic Nuxt shell | 6.0 |
| Phase 4 | friend/DM/group DM/presence/read state | 4.0 |
| Phase 5 | attachment/emoji/reaction/sticker skeleton | 3.0 |
| Phase 6 | thread/forum/onboarding/moderation/audit | 6.0 |
| Phase 7 | voice signaling/SFU integration/soundboard/stage | 7.0 |
| Phase 8 | premium entitlement/shop/quests skeleton | 3.0 |
| Phase 9 | hardening/performance/security/e2e stabilization | 4.0 |
| Phase 10 | PWA/desktop/native-mobile frontend surfaces | 7.0 |
| 합계 | Enterprise-grade V1~V3 + multi-platform frontend | 48.5 MM |

현실적 1인 바이브 코딩 기준:

- MVP: 8~12주
- Discord-like V1: 4~6개월
- Voice 포함 V2/V3: 8~12개월

## 2. Task 완료 공통 기준

성공 기준:

- Plan/Design 문서가 존재한다.
- backend domain/application unit test 통과
- REST/WebSocket integration test 통과
- ArchUnit/Spring Modulith layer test 통과
- frontend component test 통과
- Playwright smoke/e2e 통과
- QA 결과가 analysis/report 문서에 기록됨
- 실패가 있으면 feedback 문서 작성 후 재구현

실패 기준:

- 컴파일만 성공하고 행위 테스트가 없다.
- 권한/보안 시나리오 테스트가 없다.
- UI route가 실제 API와 연결되지 않았다.
- Gateway event가 API write 결과와 불일치한다.
- QA 실패를 문서화하지 않았다.

## 3. 세부 Task

### T00. Project Bootstrap

예상: 1.5 MM

범위:

- Spring Boot multi-module skeleton
- Nuxt 3 app
- Docker Compose: PostgreSQL, Redis, Redpanda, MinIO
- CI workflow
- common test fixtures
- docs/PDCA structure

성공 기준:

- `./gradlew test` 통과
- `pnpm test` 통과
- `pnpm e2e` smoke 통과
- ArchUnit baseline test 통과
- local compose healthcheck 통과

실패 기준:

- 테스트 DB 없이 mock만 존재
- frontend/backend가 독립 실행되지 않음
- 문서/QA 디렉터리 누락

### T01. Identity/User

예상: 2.0 MM

범위:

- signup/login/logout
- JWT access/refresh
- user profile
- session/device
- privacy baseline

성공 기준:

- password hashing test
- token expiry/refresh/revocation test
- invalid login lockout test
- login UI component + e2e

실패 기준:

- refresh token rotation 없음
- 인증 실패가 모호하게 처리됨
- frontend token storage 정책이 `httpOnly refresh cookie + in-memory access token` 기준을 따르지 않음

### T02. Guild/Channel/Permission

예상: 5.0 MM

범위:

- guild CRUD
- membership
- role CRUD
- permission bitset
- channel/category CRUD
- channel overwrite

성공 기준:

- permission truth table test 90% branch coverage
- role hierarchy test
- channel visibility API test
- Nuxt server rail/channel sidebar 화면 테스트

실패 기준:

- ADMINISTRATOR 우회 규칙 누락
- API는 허용하고 UI는 숨기는 불일치
- role hierarchy 역전 가능

### T03. Invite

예상: 1.5 MM

범위:

- invite create/delete/accept
- max_age/max_uses
- temporary membership
- preview
- role grant skeleton

성공 기준:

- expired invite reject test
- max uses race condition test
- accept invite creates membership exactly once
- invite modal component test

실패 기준:

- 초대 code 충돌 처리 없음
- 삭제된 invite 재사용 가능
- 권한 없는 사용자가 invite 생성 가능

### T04. Message

예상: 3.5 MM

범위:

- message create/update/delete
- pagination
- mention parse
- pin
- edit history
- basic search

성공 기준:

- cursor pagination 중복/누락 테스트
- permission denied write/read test
- mention extraction test
- chat viewport component test

실패 기준:

- offset pagination만 사용
- 삭제 메시지 처리 정책 없음
- 메시지 작성 후 Gateway event 불일치

### T05. Gateway

예상: 2.5 MM

범위:

- WebSocket auth
- heartbeat
- READY
- event sequence
- reconnect/resume
- message/channel/guild event fanout

성공 기준:

- heartbeat timeout test
- reconnect/resume integration test
- unauthorized subscription 차단
- Nuxt gateway store test

실패 기준:

- session resume 불가
- 같은 이벤트 중복 적용
- 권한 없는 채널 이벤트 수신

### T06. Nuxt Discord Shell

예상: 2.5 MM

범위:

- server rail
- channel sidebar
- chat viewport
- member sidebar
- user panel
- responsive layout

성공 기준:

- Storybook stories
- component tests
- Playwright: login -> guild -> channel -> send message
- visual smoke screenshot

실패 기준:

- 모바일에서 레이아웃 붕괴
- 실제 API/Gateway와 연결되지 않음
- 접근성 기본 focus/keyboard 누락

### T07. Friendship/DM/Group DM

예상: 4.0 MM

범위:

- friend request lifecycle
- block/privacy
- DM channel
- group DM
- group call state skeleton

성공 기준:

- blocked user cannot DM
- group member add/remove authorization
- DM list UI test
- group DM e2e

실패 기준:

- 친구 상태 전이 오류
- block 후 기존 DM에서 신규 메시지/초대/멘션 차단 정책이 적용되지 않음
- group DM membership race condition

### T08. Presence/Typing/Read State

예상: 2.0 MM

범위:

- online/idle/dnd/offline
- typing event
- read marker
- unread count

성공 기준:

- Redis TTL presence test
- typing expires automatically
- unread count deterministic test
- UI badge/component test

실패 기준:

- offline transition 누락
- typing event 영구 잔존
- unread count 불일치

### T09. Attachments/Storage

예상: 2.0 MM

범위:

- presigned upload
- attachment metadata
- download URL
- image preview
- file validation

성공 기준:

- size/type validation test
- orphan file cleanup test
- attachment send e2e

실패 기준:

- 사용자가 임의 object key 접근 가능
- 업로드 성공/메시지 실패 orphan 누락

### T10. Emoji/Reactions/Stickers

예상: 3.0 MM

범위:

- emoji CRUD
- reaction add/remove/list
- sticker skeleton
- expression permissions

성공 기준:

- duplicate reaction idempotency test
- custom emoji permission test
- reaction UI test

실패 기준:

- 권한 없이 expression 생성 가능
- reaction count race condition

### T11. Thread/Forum

예상: 3.0 MM

범위:

- public/private thread
- auto archive
- forum post/tag/guidelines/layout

성공 기준:

- thread permission inheritance test
- archive/reopen test
- forum tag requirement test
- forum UI e2e

실패 기준:

- parent channel 권한 무시
- archived thread write 가능

### T12. Onboarding/AutoMod/Audit

예상: 3.0 MM

범위:

- onboarding question
- role/channel assignment
- AutoMod keyword/spam
- audit log

성공 기준:

- AutoMod blocks before persist
- onboarding role assignment test
- audit log created for admin actions
- moderation UI test

실패 기준:

- 차단된 메시지가 저장됨
- admin action 추적 불가

### T13. Voice/SFU

예상: 5.0 MM

범위:

- voice state
- LiveKit room token
- join/leave signaling
- mute/deaf/speaking
- screen share skeleton

성공 기준:

- voice permission test
- token only issued for allowed channel
- two-browser Playwright media smoke where possible
- voice state Gateway event test

실패 기준:

- 권한 없는 사용자가 voice token 획득
- leave 후 voice state 잔존

### T14. Stage/Soundboard/Premium Skeleton

예상: 5.0 MM

범위:

- stage topic/speaker/audience/moderator
- request to speak
- soundboard sounds/play event
- entitlement model
- shop/catalog skeleton
- quests skeleton

성공 기준:

- stage state transition test
- soundboard permission test
- entitlement feature gate test
- stage UI e2e

실패 기준:

- audience가 승인 없이 speak 가능
- premium feature gate 우회

### T15. Operational Hardening/E2E Stabilization

예상: 4.0 MM

범위:

- request correlation id
- security response headers
- API cache-control baseline
- frontend API request id propagation
- regression QA evidence stabilization

성공 기준:

- every API response has `X-Request-Id`
- trusted incoming request id is echoed after sanitization
- security headers are present on API responses
- frontend API client sends request id
- full backend/frontend/e2e QA remains green

실패 기준:

- response lacks correlation id
- unsafe request id is reflected
- security headers are missing on API responses
- frontend request id behavior is untested

### T16. Persistence/PostgreSQL Migration

예상: 6.0 MM

범위:

- PostgreSQL schema/migration baseline
- repository ports/adapters for auth, guild, channel, message, invite
- transaction boundary and unique constraints
- Testcontainers/PostgreSQL integration tests
- local Docker Compose DB bootstrap using `dev_user` / `dev_password`

성공 기준:

- auth/guild/message/invite critical paths survive application restart
- message cursor pagination remains deterministic on PostgreSQL
- reaction/thread uniqueness is enforced by database constraints
- repository integration tests run against PostgreSQL, not in-memory only
- `qa/api-smoke.ps1` passes against persistence-backed backend

실패 기준:

- in-memory state remains the source of truth for persisted entities
- no migration rollback/forward strategy exists
- repository tests use mocks instead of PostgreSQL
- duplicate messages/reactions/roles are possible under concurrency

### T17. Observability/Structured Logging

예상: 2.0 MM

범위:

- `X-Request-Id` mapped to logging MDC
- structured JSON log baseline
- API error logs with request/user/guild context where safe
- lightweight metrics for API latency and auth failures
- runtime smoke evidence with correlation id

성공 기준:

- every API log line can be correlated by request id
- unsafe or sensitive data is not logged
- auth failures and forbidden actions are observable
- tests prove MDC is populated/cleared per request

실패 기준:

- request id is only returned to clients but absent from logs
- tokens/passwords/message bodies are logged in plaintext
- MDC leaks across requests

### T18. Realtime Media/Gateway Broadcast Integration

예상: 7.0 MM

범위:

- replace voice token skeleton with real LiveKit-compatible signing boundary
- voice state gateway broadcast
- soundboard play event broadcast to voice participants
- stage speaker/audience event fanout
- two-browser Playwright realtime smoke where feasible

성공 기준:

- LiveKit token signer is isolated behind a provider interface
- unauthorized users cannot receive voice/stage/soundboard events
- soundboard/stage state changes publish gateway events
- two-browser smoke validates event propagation

실패 기준:

- real media secrets leak into code/tests
- Spring attempts to handle SFU media plane directly
- hidden-channel users receive voice/stage events

### T19. Deployment Security/Abuse Controls

예상: 3.0 MM

범위:

- Nuxt HTML CSP/deployment headers
- rate limiting for auth, invite accept, message create, and gateway identify
- abuse-oriented error response policy
- Redis-backed limiter design for production parity

성공 기준:

- API and HTML responses both have documented security headers
- brute-force auth attempts are rate-limited before lockout abuse
- message spam and invite accept bursts are throttled
- rate limit tests cover user/IP/key dimensions

실패 기준:

- security headers apply only to JSON API responses
- rate limit counters are only local process memory for production path
- limiter bypass is possible by changing endpoint shape

### T20. Premium Billing/Entitlement Persistence

예상: 4.0 MM

범위:

- persistent entitlement model
- feature flag policy
- billing provider port skeleton
- subscription lifecycle states
- premium audit events

성공 기준:

- premium gate uses persisted entitlements and expiry
- duplicate entitlement grants are idempotent
- billing provider failures do not unlock features
- entitlement changes emit audit events

실패 기준:

- client can self-grant premium outside test profile
- expired entitlement remains active
- catalog/shop API implies real payment without provider boundary

### T21. Audit/Security Actions Expansion

예상: 3.0 MM

범위:

- audit coverage for guild/channel/role/message/invite/expression/stage actions
- Activity Alerts skeleton
- Security Actions skeleton for suspicious behavior
- admin audit search/filter API

성공 기준:

- privileged writes produce audit entries
- AutoMod, role assignment, invite delete, message moderation, and stage moderation are searchable
- security action tests prove alert generation without false persistence mutation

실패 기준:

- admin action can mutate state without audit trace
- audit entries omit actor/target/action/time
- alerts are generated after unsafe mutation instead of before/around policy decision

### T22. Toolchain/Build Maintenance

예상: 1.5 MM

범위:

- Gradle 9 deprecation cleanup
- Nuxt sourcemap warning triage
- Vue package export deprecation tracking
- CI warning budget/report

성공 기준:

- Gradle test/build runs without deprecation warnings under `--warning-mode all`
- frontend build warnings are either fixed or pinned with upstream issue references
- CI exposes warning regression as a visible artifact

실패 기준:

- warnings remain only as tribal knowledge in reports
- build upgrades break without prior warning inventory

### T23. Frontend Real API Integration Stabilization

예상: 4.0 MM

범위:

- replace deterministic Pinia-only shell actions with REST-backed flows incrementally
- auth token handling policy in browser
- API error display and retry policy
- runtime Playwright flow against real backend

성공 기준:

- login -> create guild/channel -> send message -> voice/stage smoke runs through real backend
- frontend request ids are visible in backend logs after T17
- API errors are surfaced accessibly in UI
- mocked and real-backend tests are clearly separated

실패 기준:

- UI can show success while backend rejected the action
- access tokens are persisted insecurely
- Playwright only validates local store mutations

### T24. Real Backend QA Orchestration

예상: 1.0 MM

범위:

- backend health/startup orchestration for real-backend QA
- PostgreSQL dev database env override for `dev_user` / `dev_password`
- API smoke and real-backend Playwright execution in one harness
- artifact log capture for backend, API smoke, and Playwright

성공 기준:

- one command can run API smoke and real-backend Playwright with consistent env
- harness can reuse an already-running backend or start `:backend:boot:bootRun`
- failures point to artifact logs
- contract test verifies command wiring without requiring external services

실패 기준:

- real-backend QA still requires manual env copy/paste
- harness kills a backend it did not start
- PostgreSQL 5432 dev convention is ignored

### T25. CI QA Harness Wiring

예상: 1.0 MM

범위:

- GitHub Actions workflow baseline
- backend/frontend unit/build jobs
- real-backend QA harness job with PostgreSQL service
- toolchain warning scan job with artifact upload
- cross-platform PowerShell QA script portability

성공 기준:

- CI workflow runs backend tests, frontend tests/build, real-backend smoke, and warning scan
- PostgreSQL service uses `discord` / `dev_user` / `dev_password`
- QA artifacts are uploaded on success or failure
- workflow structure is covered by a local contract test

실패 기준:

- CI only runs compile/build without runtime smoke
- QA scripts remain Windows-only
- CI failures lose backend/Playwright/warning logs

### T26. Nuxt SSR CSP Nonce Hardening

예상: 1.0 MM

범위:

- Nuxt HTML script CSP nonce generation
- SSR inline script nonce injection
- removal of script `unsafe-inline`
- frontend hydration and real-backend QA regression

성공 기준:

- HTML CSP uses `script-src 'self' 'nonce-...'`
- script `unsafe-inline` is not present in CSP
- rendered script tags carry the matching nonce
- login/app-shell/real-backend Playwright remains green

실패 기준:

- hydration breaks
- arbitrary inline script remains allowed
- CSP nonce and script tag nonce diverge

### T27. Multi-Platform Frontend Architecture & Screen Contracts

예상: 1.5 MM

범위:

- Nuxt web shell을 기준으로 PWA, 데스크톱, 네이티브 모바일 화면 정보구조 재정의
- shared `api-client`, `design-tokens`, `ui-contracts`, `platform-shell` package 경계 설계
- platform capability matrix: notification, deep link, tray, offline shell, push, background media/session, file picker
- route/screen contract와 permission/error/unread/presence 표현 규칙 문서화
- mobile/desktop/native screen QA 기준 추가

성공 기준:

- 웹, PWA mobile, Tauri desktop, Expo native 후보의 화면 IA와 navigation contract가 문서화됨
- platform별 분기 기준이 capability contract로 표현됨
- 기존 Nuxt shell을 재사용할 부분과 분리할 부분이 명시됨
- 이후 T28~T30 구현 task가 독립적으로 실행 가능한 성공/실패 기준을 가짐

실패 기준:

- 플랫폼별 화면이 단순 복붙으로 정의되어 유지보수 경계가 없음
- PWA와 native mobile 선택 기준이 불명확함
- API/권한/디자인 토큰 계약 없이 UI가 플랫폼마다 달라짐

### T28. PWA & Mobile Web Shell

예상: 2.0 MM

범위:

- Nuxt PWA manifest/service worker/offline shell
- 모바일 viewport용 single-pane chat, drawer channel navigation, bottom navigation
- safe-area, touch target, mobile keyboard/composer interaction
- Playwright mobile viewport smoke와 installability 검증

성공 기준:

- mobile viewport에서 login -> guild/channel -> message flow가 통과
- PWA manifest와 service worker가 테스트로 검증됨
- channel/member/voice navigation이 모바일에서 접근 가능함
- offline shell이 최소한의 loading/error/retry 상태를 제공함

실패 기준:

- 모바일에서 핵심 channel/message UI가 숨겨지거나 unreachable 상태가 됨
- PWA 설치 요건이 문서만 있고 검증되지 않음
- desktop CSS breakpoint가 mobile UX를 깨뜨림

### T29. Tauri Desktop App Shell

예상: 1.5 MM

범위:

- `apps/desktop` Tauri 2 shell scaffold
- Nuxt web build/dev server loading strategy
- desktop capability adapter: notification, invite deep link placeholder, tray/window state skeleton
- desktop smoke test and packaging contract

성공 기준:

- desktop shell이 Nuxt app을 로드하고 기본 app-shell smoke를 통과
- Tauri capability allowlist가 최소 권한으로 문서화/검증됨
- OS notification/deep link/tray 기능은 adapter boundary로 분리됨
- 웹 코드가 desktop-only API에 직접 의존하지 않음

실패 기준:

- desktop shell이 web app을 깨뜨리거나 별도 UI fork가 됨
- Tauri allowlist가 과도하게 열림
- native API 호출이 Nuxt components에 직접 흩어짐

### T30. Native Mobile App Decision & Expo Shell Spike

예상: 2.0 MM

범위:

- PWA로 충분한 기능과 native가 필요한 기능을 decision record로 분리
- `apps/mobile` Expo React Native 후보 shell
- shared API/design token/ui-contract 적용성 검증
- mobile navigation stack, auth screen, guild/channel/message read-only smoke
- push/background/media/file picker capability gap 분석

성공 기준:

- PWA-only, PWA-first+native-later, native-parallel 중 선택 근거가 문서화됨
- Expo shell이 shared API contract와 design token subset을 소비함
- native mobile navigation/component tests가 최소 shell을 검증함
- native 전환 시 추가 맨먼스와 리스크가 산정됨

실패 기준:

- native 도입이 명확한 제품/기술 근거 없이 결정됨
- web/PWA와 native가 API contract를 공유하지 못함
- push/background/media 같은 native-only 요구가 분석되지 않음

## 4. 자동 반복 루프 운영

각 task 진행 순서:

1. Plan 문서 생성/갱신
2. Design 문서 생성/갱신
3. 테스트 먼저 작성
4. 구현
5. backend/frontend/e2e QA 실행
6. 실패 시 `docs/05-feedback/{task}.feedback.md` 작성
7. feedback 기준 재구현
8. 통과 시 report 작성
9. 다음 task 진행

## 5. 즉시 시작 가능한 첫 작업

다음 작업은 설계 승인 후 시작한다.

- T00. Project Bootstrap

T00 산출물:

- Spring Boot multi-module baseline
- Nuxt app baseline
- Docker Compose
- CI
- test harness
- first Playwright smoke
- first PDCA report
