# Discord Clone Platform Enterprise Design

작성일: 2026-05-13  
PDCA Phase: Design  
대상: Spring Boot backend + Nuxt/PWA/Desktop/Mobile frontend

## 1. 아키텍처 결정

### 1.1 선택안

선택: 모듈러 모놀리스 기반 엔터프라이즈 모노레포 + 독립 인프라 컴포넌트

이유:

- Discord급 기능은 bounded context가 많지만, 초기부터 완전 MSA로 쪼개면 개발 속도와 데이터 정합성이 급격히 나빠진다.
- Spring Boot는 modular monolith에서 transaction boundary와 domain/application 계층을 엄격히 유지하기 좋다.
- Gateway, Media, Search, Storage는 성격상 독립 스케일이 필요하므로 별도 런타임/인프라로 분리한다.
- 향후 context별 DB schema와 outbox/event를 기준으로 서비스 분리가 가능하다.

대안:

- Full Monolith: 초기 개발은 빠르지만 Gateway/Media/Search 병목이 크다.
- Full Microservices: 확장성은 좋지만 초기 복잡도, 분산 트랜잭션, 배포 비용이 과도하다.

## 2. 기술 스펙

### 2.1 Backend

- Java 21
- Spring Boot 3.x
- Spring Modulith
- Spring Security
- Spring Web MVC + WebSocket/STOMP or raw WebSocket Gateway
- Spring Data JPA + QueryDSL
- PostgreSQL 16
- Redis 7
- Kafka or Redpanda
- Flyway
- OpenAPI/Swagger
- Testcontainers
- ArchUnit

### 2.2 Frontend

- Nuxt 3
- Vue 3 Composition API
- TypeScript
- Pinia
- Vue Query or TanStack Query Vue
- Tailwind CSS with project design tokens
- Vitest
- Vue Test Utils
- Playwright
- Storybook

### 2.2.1 Cross-Platform Frontend

- PWA: Nuxt app에 manifest, service worker, offline shell, installability, mobile viewport QA를 추가한다.
- Desktop app: Tauri 2를 기본 desktop shell로 채택한다. Electron은 native module/enterprise compatibility가 Tauri로 부족할 때만 재검토한다.
- Native mobile option: Expo React Native를 1차 후보로 둔다. PWA로 해결되지 않는 push notification, background media/session, app store 배포, native share/file picker 요구가 확정될 때 `apps/mobile`로 확장한다.
- Shared packages: `packages/api-client`, `packages/design-tokens`, `packages/ui-contracts`, `packages/platform-shell`로 API 호출, 디자인 토큰, route/screen contract, 플랫폼 capability contract를 공유한다.
- Screen strategy: 웹은 multi-pane, 모바일은 single active pane + drawer/bottom navigation, 데스크톱은 wide multi-pane + OS integration을 기준으로 한다.

### 2.3 Realtime

- Backend Gateway: Spring WebSocket initially
- Event bus: Kafka/Redpanda for cross-node fanout
- Presence/session: Redis
- Message fanout: Gateway node subscription registry
- Resume support: event sequence + bounded replay buffer

### 2.4 Media

- WebRTC SFU: LiveKit recommended for initial implementation
- TURN/STUN: coturn
- Media service integration via JWT room token
- Spring Boot owns authorization and voice state; SFU owns RTP media plane

### 2.5 Storage/Search

- Object storage: MinIO local, S3 compatible in production
- CDN abstraction: signed URL
- Search: OpenSearch for messages later; PostgreSQL full-text for MVP

### 2.6 Infra

- Docker Compose for local development
- GitHub Actions
- Kubernetes manifests after MVP
- Terraform after cloud target is selected

## 3. 스펙 회귀/역추적 분석

| 요구사항 | 선택 스펙 | 선택하지 않을 경우 회귀 |
| --- | --- | --- |
| 권한이 모든 기능에 영향을 줌 | Permission Engine + bitset + ArchUnit | API/UI 권한 불일치, 보안 결함 |
| 실시간 메시지/상태 | WebSocket Gateway + Redis session + Kafka fanout | 단일 노드 한계, 이벤트 유실, 재연결 불가 |
| 메시지 대량 저장 | PostgreSQL partition-ready schema + cursor pagination | offset pagination 성능 저하, 중복/누락 |
| 음성/영상 | LiveKit SFU + Spring authorization | Spring 단독 media 처리 불가능, 브라우저 다자통화 품질 저하 |
| 빠른 초기 개발 | Modular Monolith | Full MSA 대비 개발/QA/배포 복잡도 절감 |
| 향후 확장 | Bounded context + outbox event | 모놀리스 내부 결합으로 분리 불가 |
| 화면 품질 | Nuxt + component test + Storybook + Playwright | 구현 후 UX 회귀 탐지 불가 |
| 멀티 플랫폼 화면 확장 | Nuxt PWA + Tauri desktop + Expo 후보 + shared UI contracts | 웹 전용 컴포넌트가 모바일/데스크톱 확장을 막고 플랫폼별 UX가 분기됨 |
| QA 자동 반복 | PDCA docs + feedback loop | 실패 원인 누락, 같은 결함 반복 |

## 4. 모노레포 구조

```text
discord/
  apps/
    web/                         # Nuxt 3 app
    desktop/                     # Tauri 2 shell for Nuxt web build
    mobile/                      # Expo React Native candidate shell
  backend/
    boot/                        # Spring Boot app entry
    modules/
      identity/
      user/
      friendship/
      guild/
      channel/
      permission/
      invite/
      message/
      gateway/
      presence/
      expression/
      moderation/
      voice/
      notification/
      audit/
    shared/
      common/
      test-support/
  packages/
    api-client/
    design-tokens/
    ui-contracts/
    platform-shell/
  infra/
    docker/
    k8s/
    terraform/
  docs/
    00-research/
    01-plan/
    02-design/
    03-analysis/
    04-report/
    05-feedback/
  qa/
    harness/
    playwright/
```

## 5. Backend bounded contexts

### identity

- email/password auth
- JWT access/refresh
- session/device
- password reset

### user

- profile
- status
- settings
- privacy

### friendship

- friend request
- accept/reject/block
- DM eligibility

### guild

- server lifecycle
- membership
- role
- guild settings

### permission

- permission bitset
- role hierarchy
- channel overwrite
- authorization policy

### channel

- channel/category/thread/forum/stage models
- channel ordering
- channel visibility

### invite

- invite code
- max age/max uses
- temporary membership
- target user/role grant

### message

- message write/read
- attachment metadata
- mention
- reaction
- poll
- pin

### gateway

- WebSocket sessions
- event routing
- heartbeat/resume
- fanout

### presence

- online/idle/dnd/offline
- typing
- voice state

### expression

- emoji
- sticker
- soundboard

### moderation

- AutoMod
- report
- timeout/ban/kick
- activity alert

### voice

- voice room authorization
- SFU token issue
- voice state sync

## 6. 핵심 데이터 모델

```text
users
user_profiles
user_settings
friendships

guilds
guild_members
roles
member_roles
permissions

channels
channel_permission_overwrites
threads
forum_tags

invites
invite_target_users
invite_role_grants

messages
message_edits
message_attachments
message_mentions
message_reactions
message_polls
message_poll_votes
message_pins

gateway_sessions
presence_states
typing_states
voice_states

emojis
stickers
soundboard_sounds

automod_rules
moderation_actions
audit_logs
```

## 7. API 설계 원칙

- REST API는 command/query를 분리한다.
- 모든 write API는 권한 검사를 application service 진입점에서 수행한다.
- 모든 write는 audit event를 남긴다.
- 실시간 반영은 REST 성공 후 domain event -> outbox -> Gateway event로 전파한다.
- client는 REST로 initial snapshot을 받고 Gateway로 delta를 받는다.

예시 endpoint:

```text
POST   /api/auth/login
GET    /api/users/@me
POST   /api/guilds
GET    /api/guilds/{guildId}
POST   /api/guilds/{guildId}/channels
POST   /api/channels/{channelId}/messages
GET    /api/channels/{channelId}/messages?before=&limit=
POST   /api/channels/{channelId}/invites
POST   /api/invites/{code}/accept
WS     /gateway
POST   /api/voice/channels/{channelId}/join
```

## 8. Frontend 구조

```text
apps/web/
  app.vue
  pages/
    login.vue
    app.vue
    guilds/[guildId]/channels/[channelId].vue
  components/
    shell/
      ServerRail.vue
      ChannelSidebar.vue
      ChatViewport.vue
      MemberSidebar.vue
      UserPanel.vue
    message/
    guild/
    channel/
    voice/
  composables/
    useGateway.ts
    usePermission.ts
    useMessages.ts
  stores/
    auth.store.ts
    gateway.store.ts
    guild.store.ts
    message.store.ts
  tests/

apps/desktop/
  src-tauri/
    tauri.conf.json
    capabilities/
  src/
    main.ts                      # desktop bootstrap and webview bridge
    tray.ts
    notifications.ts
  tests/

apps/mobile/
  app/
    (auth)/
    (main)/
      guilds/
      channels/
      dm/
  components/
    shell/
    message/
    voice/
  navigation/
  tests/

packages/ui-contracts/
  screens.ts                     # route, pane, navigation, capability contracts
  platform.ts

packages/platform-shell/
  pwa/
  desktop/
  mobile/
```

### 8.1 Platform Screen Rules

- Web desktop: server rail, channel sidebar, chat viewport, member sidebar, user/voice panel을 동시에 노출한다.
- Mobile PWA: server/channel navigation은 drawer 또는 bottom navigation으로 접고, chat 또는 voice 화면 하나를 primary pane으로 유지한다.
- Desktop app: web desktop layout을 유지하되 window state, tray reconnect, OS notification, invite deep link를 shell capability로 분리한다.
- Native mobile: Expo screen stack은 PWA mobile IA를 따른다. 단, push/background/media/file picker 같은 native capability만 platform adapter로 분기한다.
- 공통: 권한, presence, unread, route guard, API error 표현은 플랫폼별 재구현이 아니라 shared contract를 통해 검증한다.

## 9. QA 설계

### Backend

- JUnit 5
- AssertJ
- Mockito only at boundary
- Testcontainers PostgreSQL/Redis
- ArchUnit layer test
- Spring Modulith application module test
- REST Assured contract test
- WebSocket integration test

### Frontend

- Vitest component tests
- Storybook interaction tests
- Playwright e2e
- visual screenshot smoke for app shell
- accessibility smoke with axe
- Playwright mobile viewport smoke for PWA shell
- PWA manifest/service worker/installability smoke
- Tauri desktop shell smoke for boot, window title, deep link placeholder, notification adapter boundary
- Expo/native candidate component and navigation contract tests when `apps/mobile` is promoted

### Cross-system

- docker-compose integration environment
- seed data fixtures
- API contract generation
- QA failure feedback 문서 자동 템플릿

## 10. 구현 순서

1. Repository/bootstrap/test harness
2. Identity
3. Guild/Channel/Permission
4. Message
5. Gateway
6. Nuxt app shell
7. Invite
8. Friendship/DM
9. Presence/Typing/Read state
10. Attachments
11. Emoji/Reactions
12. Thread/Forum
13. Moderation/AutoMod
14. Voice signaling/SFU
15. Stage/Soundboard/Premium skeleton
16. Multi-platform frontend architecture and screen contracts
17. PWA/mobile web shell
18. Tauri desktop shell
19. Native mobile app decision/Expo shell spike

## 11. 설계 승인 게이트

이 문서는 구현 전 기준선이다. 구현을 시작하려면 다음을 승인해야 한다.

- 초기 구조를 모듈러 모놀리스로 시작한다.
- Gateway/Media/Search/Storage는 독립 인프라로 둔다.
- LiveKit을 음성/영상 SFU 후보로 둔다.
- task마다 TDD + 아키텍처 테스트 + 화면 테스트를 완료 기준으로 둔다.
- PWA 우선, Tauri 데스크톱 shell, Expo 네이티브 후보를 멀티 플랫폼 프론트 기준선으로 승인한다.
