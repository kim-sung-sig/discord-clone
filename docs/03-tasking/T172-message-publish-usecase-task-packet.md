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
- `MessagePublicationStore`
- `MessagePublishGuard`
- `MessageMutationGuard`
- `MessageContentPolicy`
- `ChannelMessageReader`
- `ChannelMessageReadGuard`
- `ChannelMessagePagePort`

Persistence/runtime profiles:

- default profile: `InMemoryMessageService`는 local/default 포트 구현으로만 유지한다.
- postgres profile: `JdbcMessageStore`가 `MessageStore`, `ChannelMessagePagePort`, `ChannelMessageSearchPort`, `MessageLookupPort`, `MessagePublicationOutbox`를 직접 구현한다.
- postgres profile: legacy `PersistentMessageService(loadAll + in-memory snapshot)` 경로는 제거한다.

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
- postgres profile에서 JDBC message adapter가 store/read/search/lookup/outbox 포트를 제공
- publish use case가 메시지 저장과 outbox 이벤트 저장을 `MessagePublicationStore.savePublished(...)` 하나의 포트 호출로 위임
- idempotency key를 `message_idempotency_keys`에 TTL과 함께 저장하고 만료 key는 재시도 판정에서 제외
- `message_publication_outbox`와 mention target outbox row 저장
- outbox relay는 unpublished row를 bounded batch로 claim하고, 성공분만 `published_at`을 갱신
- relay 실패 시 claim을 해제하고 attempts/last_error/dead-letter 상태를 갱신
- relay 실패 후 retry backoff 전에는 같은 publication을 다시 claim하지 않음
- internal operator header 없이는 dead-letter 조회/replay API 접근 거절
- internal operator header가 있으면 dead-letter publication을 조회하고 replay 요청으로 relay 대상에 다시 올림
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
- `JdbcMessageStoreTest`: postgres profile JDBC adapter/store/read/search/lookup/outbox 경계 없음
- `DefaultPublishMessageUseCaseTest`: 기존 publish use case가 `MessageStore.save(...)`와 `MessagePublicationOutbox.append(...)`를 별도 호출하던 구조와 충돌
- `DefaultMessagePublicationRelayTest`: relay/dispatcher/mark-published 경계 없음

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
- message search, reaction, attachment 경로가 `InMemoryMessageService` 타입을 직접 의존하지 않도록 `ChannelMessageSearchPort`, `MessageLookupPort`로 전환
- `V9__message_clean_persistence_ports.sql`로 mention target, idempotency TTL, outbox, outbox mention schema 추가
- postgres profile에서 `JdbcMessageStore`가 메시지 저장/조회/검색/lookup/idempotency/outbox 포트를 제공하도록 구현
- legacy `PersistentMessageService`, `MessageSnapshotStore`, `JdbcMessageSnapshotStore`, `PostgresMessageServiceTest` 제거
- `MessagePublicationStore`를 추가하고 `DefaultPublishMessageUseCase`가 idempotent publish 저장과 `MessagePublished` outbox 저장을 `savePublished(...)` 한 번으로 위임하도록 전환
- `JdbcMessageStore.savePublished(...)`는 동일 JDBC connection/transaction 안에서 message, mentions, edits, idempotency key, outbox, outbox mentions를 저장
- `MessagePublicationRelay`, `MessagePublicationOutboxQueue`, `MessagePublishedDispatcher`, `ClaimedMessagePublication` 추가
- relay는 claim lease 기반 bounded batch만 처리하고, dispatcher 성공 후 claim token으로 `published_at`을 갱신
- 실패한 dispatch는 published 처리하지 않고 claim을 해제하며 failure metadata를 남긴다
- `V10__message_outbox_claims.sql`로 outbox claim token, claim lease, attempts, last_error, dead_lettered_at 추가
- `MessagePublicationRelayWorker`가 fixed-delay scheduled relay를 실행한다
- relay 실패는 `discord.message.outbox-relay-retry-delay-ms` 기본 5000ms backoff를 적용해 같은 publication의 즉시 재시도 폭주를 막는다
- `JdbcMessageStore.releaseFailed(...)`는 실패 시 `claim_expires_at = failedAt + retryDelay`를 저장하고, dead-letter 확정 시에는 claim/retry 상태를 비운다
- local/default 포트 구현도 실패 publication을 retry delay 이후에만 다시 claim한다
- `MessagePublicationDeadLetterQueue`, `DeadLetteredMessagePublication` 추가
- `MessageOutboxController`가 `X-Internal-Message-Outbox-Operator` 헤더와 `discord.message.outbox-operator-token`으로 보호되는 dead-letter 조회/replay API를 제공
- replay 요청은 dead-letter 상태를 해제하고 attempts/error/claim 상태를 초기화해 relay가 다시 claim할 수 있게 함
- `JdbcMessageStore`가 dead-letter 조회/requeue 포트를 제공하고, local/default 포트 구현도 동일 계약을 제공

## 보안/확장성 확인

- mutating endpoint의 bearer identity/permission 체크는 이번 슬라이스에서 제거하지 않았다.
- mention의 진실은 content 파싱 결과가 아니라 요청으로 들어온 structured mention target이라는 결정에 맞춰 기존 서버-side mention extraction 기대를 제거했다.
- `ChannelMessageQuery`는 requester와 target을 명시하고 limit을 양수로 제한한다.
- `IdempotencyKey`는 content hash가 아니라 클라이언트 발송 시도 ID로 유지한다.
- 화면/클라이언트는 발송 시도마다 `clientEventId`를 만들고, 같은 값을 `idempotencyKey`로 서버에 전송한다. 재시도 시 같은 client event/idempotency key를 재사용할 수 있다.
- 중복 판정 범위는 `MessageAuthor + MessageTarget + IdempotencyKey`다. `content` 동일 여부는 중복 판정 기준이 아니다.
- 같은 범위의 `IdempotencyKey`가 다른 payload와 함께 다시 들어오면 클라이언트 오용으로 보고 `409 Conflict`로 거절한다.
- `IdempotencyKey` 보존은 무기한이 아니다. 영속 저장 adapter에서는 `expires_at` 또는 TTL 정책으로 24시간/7일 같은 보존 기간을 두고, 만료 후 같은 content라도 새 발송 시도 key를 정상 저장할 수 있게 한다.
- postgres boot wiring은 더 이상 기존 `PersistentMessageService(loadAll + in-memory snapshot)`를 사용하지 않는다.
- default/local profile은 아직 `InMemoryMessageService`를 포트 구현으로 사용한다. 이것은 local runtime용 잔여 구현이며, 운영/DB profile의 기준 구현은 `JdbcMessageStore`다.
- `JdbcMessageStoreTest`는 `DISCORD_RUN_POSTGRES_TESTS=true`가 필요하다. 기본 로컬 검증에서는 컴파일만 확인되고 실제 PostgreSQL round-trip은 CI 또는 로컬 Postgres 제공 환경에서 실행해야 한다.
- postgres profile의 publish path는 `MessagePublicationStore.savePublished(...)`를 통해 message/idempotency/outbox 저장을 하나의 adapter transaction 경계로 묶는다.
- outbox relay는 `discord.message.outbox-relay-batch-size`로 bounded batch를 사용하고, claim lease로 다중 worker 중복 처리를 줄인다.
- outbox relay 실패는 retry backoff를 적용한다. 이 값은 `discord.message.outbox-relay-retry-delay-ms`로 조정하며, 기본 5000ms다.
- outbox dispatcher는 `MessagePublished`를 message lookup으로 보강한 뒤 gateway `MESSAGE_CREATE` 이벤트로 발행한다.
- dead-letter 조회/replay API는 내부 operator header로 보호한다. replay는 즉시 dispatch하지 않고 dead-letter 상태를 해제해 scheduled relay가 다시 claim하도록 만든다.
- 메시지 발행 자체의 SAGA/보상 트랜잭션은 T172 초기 설계에서 과설계로 판단되어 이 슬라이스에는 넣지 않는다. 현재 메시지 발행 안정성은 transactional outbox, claim lease, retry backoff, dead-letter, operator replay로 닫는다.
- SAGA 후보는 attachment upload + message attach, invite accept, premium entitlement 같은 다중 리소스 상태 변경 흐름에서 별도 task로 다룬다.
