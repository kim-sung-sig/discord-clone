# T172 메시지 발행 유스케이스 슬라이스 Task Packet

## 개요

Task ID: T172

Goal: 메시지 모듈을 기존 `guildId/channelId/authorId/String content` 중심 모델에서 `MessageAuthor`, `MessageTarget`, `MessageContent`, `MessageMentionTarget`, `IdempotencyKey` 기반 발행 모델로 전환한다.

Domain: backend/modules/message, backend/boot message adapter

Allowed write paths:

- `backend/modules/message/src/main/java/com/example/discord/message/**`
- `backend/modules/message/src/test/java/com/example/discord/message/**`
- `backend/boot/src/main/java/com/example/discord/message/**`
- `backend/boot/src/test/java/com/example/discord/message/**`
- `docs/03-tasking/**`

Forbidden changes:

- Spring/Jakarta/web 의존성을 message domain module에 추가하지 않는다.
- content 문자열에서 서버가 mention을 추출하는 흐름을 되살리지 않는다.
- 클라이언트가 보낸 `IdempotencyKey`를 content hash로 대체하지 않는다.
- 운영/boot용 신규 인메모리 메시지 저장소를 만들지 않는다.

Affected endpoints:

- 기존 message controller 응답 변환부
- 기존 message create/edit/delete/pin endpoint의 응답 content/mentions shape
- `POST /api/channels/{channelId}/messages`: `idempotencyKey`를 필수 요청 필드로 받고 `PublishMessageUseCase`로 위임한다.
- `GET /api/channels/{channelId}/messages`: `ChannelMessageReader`로 위임하고 기본 조회에서는 삭제 메시지를 숨긴다.
- `PATCH /api/channels/{channelId}/messages/{messageId}`: `EditMessageUseCase`로 위임한다.
- `DELETE /api/channels/{channelId}/messages/{messageId}`: `DeleteMessageUseCase`로 위임한다.
- `PUT/DELETE /api/channels/{channelId}/messages/{messageId}/pin`: `PinMessageUseCase`로 위임한다.

Affected domain services:

- `Message`
- `MessageEdit`
- `PublishMessageUseCase`
- `DefaultPublishMessageUseCase`
- `MessageStore`
- `MessagePublicationOutbox`
- `MessagePublishGuard`
- `MessageMutationGuard`
- `MessageContentPolicy`
- `ChannelMessageReader`
- `ChannelMessageReadGuard`
- `ChannelMessagePagePort`

Persistence/runtime profiles:

- default profile: 기존 runtime wiring은 유지하되, `Message` 모델은 새 도메인 타입으로 전환한다.
- postgres profile: 기존 snapshot table adapter가 새 도메인 타입을 조립/해체하도록 변환한다.

External dependencies:

- 없음

Expected tests:

- `PublishMessageRequest`, `EditMessageRequest`, `DeleteMessageRequest`, `PinMessageRequest`, `ChannelMessageQuery` 값 검증
- `DefaultPublishMessageUseCase` guard 거절
- 성공 발행 시 `Message` 저장
- 중복 mention 제거
- idempotent retry 기존 메시지 반환
- 같은 idempotency key + 다른 payload 거절
- 같은 content라도 다른 idempotency key면 새 message 저장
- edit/delete/pin use case guard 거절과 성공 mutation
- channel message reader read guard 거절
- channel message reader 기본 조회에서 deleted message 제외
- message module 전체 테스트
- backend boot 테스트
- root Gradle 테스트
- frontend message composer payload 테스트
- npm workspace 테스트

Expected docs/wiki updates:

- 이 task packet
- 구현 완료 후 durable architecture 변경이 있으면 외부 wiki `Backend Architecture.md`, `log.md` 갱신

## RED/GREEN 기록

RED:

- `PublishMessageRequestTest`: `PublishMessageRequest` 클래스 없음으로 컴파일 실패
- `EditMessageRequestTest`: `EditMessageRequest` 클래스 없음으로 컴파일 실패
- `DeleteMessageRequestTest`: `DeleteMessageRequest` 클래스 없음으로 컴파일 실패
- `PinMessageRequestTest`: `PinMessageRequest` 클래스 없음으로 컴파일 실패
- `ChannelMessageQueryTest`: `ChannelMessageQuery` 클래스 없음으로 컴파일 실패
- `DefaultPublishMessageUseCaseTest`: use case/port/예외 없음으로 컴파일 실패
- `DefaultPublishMessageUseCaseTest`: 성공 발행 경로가 `UnsupportedOperationException`으로 실패
- `DefaultPublishMessageUseCaseTest`: policy에 중복 mention 원본이 전달되어 실패
- `DefaultPublishMessageUseCaseTest`: 같은 idempotency key + 다른 content가 거절되지 않아 실패
- `DefaultEditMessageUseCaseTest`: 수정 유스케이스/guard/store 갱신 경계 없음으로 컴파일 실패
- `DefaultDeleteMessageUseCaseTest`: 삭제 유스케이스/guard/store 갱신 경계 없음으로 컴파일 실패
- `DefaultPinMessageUseCaseTest`: 고정 유스케이스/guard/store 갱신 경계 없음으로 컴파일 실패
- `DefaultChannelMessageReaderTest`: reader/read guard/page port 없음으로 컴파일 실패

GREEN:

- 요청 객체와 query 값 검증 구현
- `Message`가 `MessageAuthor`, `MessageTarget`, `MessageContent`, `List<MessageMentionTarget>`를 직접 품도록 전환
- `DefaultPublishMessageUseCase` 성공 저장/outbox append 구현
- mention 중복 제거를 policy/store/event에 동일 적용
- idempotent retry payload 충돌을 `MessagePublishRejectedException`으로 거절
- `MessageController` create 경로를 `PublishMessageUseCase`로 전환
- create endpoint에서 `idempotencyKey` 누락은 400, 같은 key 재시도는 기존 message 반환, 같은 key + 다른 content는 409로 검증
- 웹 클라이언트는 발송 시도마다 만든 `clientEventId`를 `idempotencyKey`로 같이 전송
- 같은 content라도 서로 다른 클라이언트 발송 시도 key면 새 메시지를 저장하고 이벤트를 각각 발행하도록 검증
- edit/delete/pin 경로를 `MessageMutationGuard` + `MessageStore` 기반 use case로 구현
- channel list 경로를 `ChannelMessageReadGuard` + `ChannelMessagePagePort` 기반 reader로 구현
- boot controller의 list/edit/delete/pin/unpin 경로를 새 use case/reader로 전환

## 보안/확장성 확인

- mutating endpoint의 bearer identity/permission 체크는 이번 슬라이스에서 제거하지 않았다.
- mention의 진실은 content 파싱 결과가 아니라 요청으로 들어온 structured mention target이라는 결정에 맞춰 기존 서버-side mention extraction 기대를 제거했다.
- `ChannelMessageQuery`는 requester와 target을 명시하고 limit을 양수로 제한한다.
- `IdempotencyKey`는 content hash가 아니라 클라이언트 발송 시도 ID로 유지한다.
- 화면/클라이언트는 발송 시도마다 `clientEventId`를 만들고, 같은 값을 `idempotencyKey`로 서버에 전송한다. 재시도 시 같은 client event/idempotency key를 재사용할 수 있다.
- 중복 판정 범위는 `MessageAuthor + MessageTarget + IdempotencyKey`다. `content` 동일 여부는 중복 판정 기준이 아니다.
- 같은 범위의 `IdempotencyKey`가 다른 payload와 함께 다시 들어오면 클라이언트 오용으로 보고 `409 Conflict`로 거절한다.
- `IdempotencyKey` 보존은 무기한이 아니다. 영속 저장 adapter에서는 `expires_at` 또는 TTL 정책으로 24시간/7일 같은 보존 기간을 두고, 만료 후 같은 content라도 새 발송 시도 key를 정상 저장할 수 있게 한다.
- 현재 boot wiring은 새 코어 포트에 연결됐지만 runtime 저장 adapter는 아직 기존 `InMemoryMessageService`를 포트 구현으로 사용한다. DB-backed `MessageStore`/`ChannelMessagePagePort`/idempotency TTL adapter는 다음 슬라이스의 잔여 작업이다.
