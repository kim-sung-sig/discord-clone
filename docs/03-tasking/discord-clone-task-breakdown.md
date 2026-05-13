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
| 합계 | Enterprise-grade V1~V3 | 41.5 MM |

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
