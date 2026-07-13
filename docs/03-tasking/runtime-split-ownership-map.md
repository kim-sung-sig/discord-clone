# Runtime Split Ownership Map

작성일: 2026-07-13  
범위: `backend:boot`의 현재 HTTP/WebSocket 진입점과 Spring 구성의 미래 런타임 소유권을 확정한다. 이 문서는 서비스를 생성하거나 API 동작을 바꾸지 않는다.

## 고정 경계

| 런타임 | 소유 데이터/책임 | 제외 책임 |
| --- | --- | --- |
| Gateway/load balancer | TLS 종료, 경로 라우팅, 서비스별 base URL, 로드 밸런싱 | Java 애플리케이션, JWT 검증, WebSocket 세션, 데이터베이스 |
| identity-service | 계정, 자격 증명, refresh token, 소셜 로그인, MFA, 프로필 | 방/채널, 메시지, 소켓 |
| message-service | guild, 채널, 멤버십, 초대, 소유자 확인, 메시지와 메시지 이벤트 | 계정 자격 증명, 소켓 |
| websocket-service | 연결 수명주기, 연결 시 JWT 검증, 구독, 이벤트 fan-out | REST 도메인 API, 도메인 데이터베이스, 방/메시지 쓰기 |
| community-service | thread/forum 및 향후 post/comment 이벤트 | 계정, 소켓 세션, 방/메시지 쓰기 |

기존 Java `gateway` 패키지는 load balancer가 아니라 realtime delivery를 뜻한다. 따라서 해당 Java 코드는 `websocket-service`로 간다.

## 소유권 인벤토리

| 현재 boot 경로 | 미래 소유자 | 데이터 소유/근거 | 처리 |
| --- | --- | --- | --- |
| `auth/AuthController.java`, `auth/AuthConfiguration.java` | identity-service | 계정, bearer JWT, refresh token, MFA, 프로필 | 이전 |
| `social/SocialController.java`, `social/SocialConfiguration.java` | identity-service | 소셜 identity와 계정 연결 | 이전 |
| `AuthenticatedUserResolver` 및 auth store/보안 구성 | identity-service | JWT 발급·검증의 원천 | 이전 |
| `guild/GuildController.java`, `guild/GuildConfiguration.java`, `guild/UserGuildController.java` | message-service | guild/채널 topology, 멤버십, visible-channel read model | 이전 |
| `invite/InviteController.java`, `invite/InviteConfiguration.java` | message-service | 초대와 멤버십 변경 | 이전 |
| `message/MessageController.java`, `message/MessageOutboxController.java`, `message/MessageConfiguration.java` | message-service | 방/채널 메시지, 메시지 outbox/event | 이전 |
| `gateway/GatewayController.java`, `gateway/GatewayConfiguration.java`, `gateway/GatewayWebSocketConfiguration.java`, `GatewayWebSocketHandler`, session registry, event-bus adapters | websocket-service | 인증된 연결, resume, 구독, fan-out | 이전 |
| `thread/ThreadController.java`, `thread/ThreadConfiguration.java` | community-service | thread/forum 상태와 이벤트 | 이전; post/comment 신설은 범위 밖 |
| `persistence/PostgresPersistenceConfiguration.java` | 서비스별 구성 | 서비스가 자기 schema/database만 연결 | 각 서비스로 분해; 공유 boot 구성으로 유지 금지 |
| `ops/ProductionSecretConfiguration.java` | 서비스별 구성 | 각 서비스의 production secret/profile 검증 | 각 서비스로 분해 |
| `presence/PresenceController.java`, `presence/PresenceConfiguration.java` | deferred | presence/read-state 확장은 현재 네 런타임 경계에 포함하지 않음 | 동결 |
| `storage/AttachmentController.java`, `storage/StorageConfiguration.java` | deferred | attachment/storage는 별도 데이터·보안 경계 필요 | 동결 |
| `expression/ExpressionController.java`, `expression/ExpressionConfiguration.java` | deferred | emoji/reaction/sticker 기능 확장 | 동결 |
| `voice/VoiceController.java`, `voice/VoiceConfiguration.java` | deferred | media/LiveKit과 음성 세션 | 동결 |
| `experience/ExperienceConfiguration.java`, `PremiumController.java`, `SoundboardController.java`, `StageController.java` | deferred | premium, stage, soundboard 경험 기능 | 동결 |
| `moderation/ModerationController.java`, `moderation/ModerationConfiguration.java` | deferred | moderation/AutoMod/audit 확장 | 동결 |

컨트롤러별 `@RestControllerAdvice`는 해당 표의 컨트롤러와 함께 이동한다. 새로운 전역 role/permission, administrator console, RBAC/ABAC는 이 추출의 소유자가 아니며 명시적으로 보류한다.

## 데이터·계약 경계

- 각 서비스는 자기 schema/database만 읽는다. 다른 서비스 데이터는 버전 있는 REST 또는 broker 이벤트 계약으로만 받는다.
- `websocket-service`는 identity-service가 발급한 JWT를 연결 시 검증한다. 메시지마다 identity-service를 조회하지 않는다.
- `message-service`는 자기 멤버십/소유권 데이터로 room/message 인가를 수행한다.
- 요청 ID와 W3C trace context는 서비스 경계를 넘어 전달한다. 로그에는 token, cookie, password, message body를 기록하지 않는다.
- Gateway/load balancer에는 Java 코드, JWT 로직, event listener, 데이터베이스 연결을 두지 않는다.

## 동결 목록

Plan 01 완료 전에는 다음 신규 작업을 시작하지 않는다.

- administrator console 또는 dashboard
- generic RBAC/ABAC, global role/permission management
- Kafka hardening 또는 broker topology 확장
- media, voice, LiveKit, soundboard 기능

현재 작업 트리의 독립 변경은 이 범위 동결의 대상이 아니며 수정·폐기하지 않는다.

## 검증 기록

`rg -n '@RestController|class .*Configuration' backend/boot/src/main/java` 결과의 모든 컨트롤러와 구성 클래스는 위 인벤토리에 포함했다. `@RestControllerAdvice`는 각 원 컨트롤러의 이동에 종속한다.
