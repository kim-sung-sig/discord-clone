# T221 메시지 작성자 이름 표시 Blueprint

## 승인 상태
- 상태: 승인됨. UI polish 계획(`C:\tmp\review-extract\ui-polish\plan.md`, Task 3)으로 승인되었다.
- 사용자 요청: "맘에 안들거나 어색한 부분 개선". 각 task는 독립 branch/worktree에서 실행하고, compile·lint·Gradle은 controller만 실행한다.
- 근거 finding: `C:\tmp\review-extract\ui-polish\ui-fix-map.md` C절. 운영 build 화면(`12-explorer.png`)에서 메시지 작성자가 `36f8493c-4c80-...` 같은 UUID로 표시되고, avatar도 UUID 앞 두 글자(`36`)로 표시된다.
- 막힌 모호성: 없음. 아래 결정으로 해소했다.

## 목표
- 메시지 REST 응답이 작성자 표시 이름(`authorDisplayName`)을 함께 내려준다. 값은 표시 이름이고, 비어 있으면 username이다. 작성자 사용자가 더 이상 없을 때만 `null`이다. 기존 `authorId`는 그대로 둔다.
- 실시간 Gateway `MESSAGE_CREATE` payload에도 같은 값을 넣는다.
- web 화면은 작성자 이름을 표시하고 avatar 이니셜도 표시 이름에서 만든다. 어떤 경우에도 raw UUID를 작성자로 보여 주지 않는다.

## 범위와 범위 밖
- 범위
  - `MessageController`의 메시지 응답 전부: 생성(POST), 목록(GET, 최신 page와 `before` cursor의 이전 page), 수정(PATCH), 고정/해제(PUT·DELETE `/pin`), 검색(GET `/search`).
  - 두 runtime 경로: `postgres` profile(`JdbcAuthStore`)과 in-memory/test profile(`InMemoryAuthStore`).
  - Gateway payload를 만드는 두 곳: local dispatcher(`MessageConfiguration`, `!kafka`)와 Kafka consumer(`KafkaMessagePublishedConsumer`).
  - web: `apps/web/stores/shell.ts`의 backend 메시지 mapping 4곳(생성 확인, 최신 page 재동기화, 이전 page, Gateway 생성 이벤트), `ChatViewport.vue` avatar 이니셜.
- 범위 밖
  - mention chip(`@<uuid>`)은 여전히 id를 보여 준다. 후속 task로 기록한다.
  - 멤버 목록 endpoint, 사용자 id 조회 endpoint 신설.
  - 메시지 정렬, 읽음 표시, 자동 scroll(T220 범위).
  - 전송 중(optimistic) 메시지의 작성자(`shell.ts`의 `author: this.currentUser`). 바로 윗줄 `sequence`가 T220의 read-marker 수정 지점이라 같은 hunk 충돌을 피하려고 이번 범위에서 제외한다. 전송 응답이 오면 확인 mapping이 서버 값으로 교체하므로 잠깐만 보인다. T220 병합 후 후속으로 처리한다.
  - DM 메시지, thread 메시지 응답.

## 설계 결정
1. **이름 해석은 message module의 outbound port로 한다.** `backend/modules/message`는 다른 module에 의존하지 않는 framework-free module이다(`build.gradle.kts`에 slf4j만 있다). users table은 `com.example.discord.auth`의 `AuthStore`(`JdbcAuthStore`/`InMemoryAuthStore`)가 소유한다.
   - `JdbcMessageStore`의 read projection SQL에 `users`를 join하면 message 저장소가 사용자 저장소의 table 구조에 결합된다. 또 in-memory 경로(`InMemoryMessageService`)와 `Message` aggregate를 쓰는 생성·수정·Gateway 경로는 join으로 해결되지 않는다. 그래서 cross-module SQL join은 쓰지 않는다.
   - message module에 `MessageAuthorDirectory` port를 새로 둔다. `Map<UUID, String> displayNames(Set<UUID> userIds)`를 한 번 호출해 page 단위로 일괄 조회한다. 단건용 default method `displayName(UUID)`도 둔다. 계약: 반환 map에는 존재하는 사용자만 들어간다. 없는 사용자는 key가 없다.
   - 구현은 boot의 `com.example.discord.auth.AuthStoreMessageAuthorDirectory`(`@Component`)다. `AuthStore`가 package-private이므로 같은 package에 둔다. "표시 이름 → 비어 있으면 username" 규칙은 사용자 저장소 쪽인 이 adapter가 소유한다.
   - 의존 방향: boot `auth` → message module port. message module은 user/auth를 모른다. `ArchitectureTest` 규칙(domain module의 boot·Spring import 금지)을 지킨다.
2. **`AuthStore`에 일괄 조회를 추가한다.** `List<UserProfile> findProfilesByIds(Set<UUID> ids)`
   - `JdbcAuthStore`: `SELECT id, username, display_name, created_at FROM users WHERE id = ANY(?)`(uuid 배열 binding, 기존 `createArrayOf("uuid", …)` 관례). 빈 입력이면 query하지 않는다. 기존 `mapProfile`을 재사용한다.
   - `InMemoryAuthStore`: `accountsById`에서 찾는다.
   - 조회 크기 상한: 호출자는 한 page의 서로 다른 작성자 id만 넘긴다. page는 최대 100건(`pageSize`), 검색은 최대 50건이다. 그래서 query는 page마다 1회이고 bounded다. N+1 조회를 하지 않는다.
3. **응답 조립은 controller에서 한다.** `MessageResponse` record에 `String authorDisplayName`을 `authorId` 바로 뒤에 추가한다. `MessageReadModel`과 store 구현(`JdbcMessageStore`, `InMemoryMessageService`)은 바꾸지 않는다. read projection은 메시지 사실만 담고, 사용자 이름은 응답 시점에 port로 붙인다. 이름이 바뀌어도 projection을 다시 쓸 필요가 없다.
   - 목록·검색은 page의 작성자 id 집합으로 port를 1회 호출한다.
   - 생성·수정·고정은 단건 조회다. 작성자가 `UserMessageAuthor`가 아니면(현재 controller 경로에는 없음) `null`이다.
4. **Gateway payload**: `authorId` 다음에 `authorDisplayName`을 넣는다. 이름이 없으면 key를 생략한다. `GatewayEvent`가 `Map.copyOf`로 payload를 복사하므로 `null` 값을 넣으면 NPE가 난다. `GatewayPayloadSanitizer`의 민감 key 목록(token, secret 등)에 걸리지 않는 이름이다.
   - `MessageConfiguration.messagePublishedDispatcher`와 `KafkaMessagePublishedConsumer` 생성자에 `MessageAuthorDirectory`를 추가한다. 기존 두 `gatewayPayload` 중복은 이번 범위에서 정리하지 않는다(최소 hunk).
5. **web mapping**
   - backend 메시지 응답 타입은 `apps/web/services/discord-api.ts`가 아니라 `apps/web/stores/shell.ts`의 `BackendMessageResponse`에 있다(`discord-api.ts`에는 path builder와 REST client만 있다). 그래서 `BackendMessageResponse`에 `authorDisplayName?: string | null`을 추가하고 `discord-api.ts`는 바꾸지 않는다.
   - 새 helper 파일 `apps/web/services/message-author.ts`(T220과 `shell.ts` 충돌을 줄이려고 분리)
     - `messageAuthorLabel(author, viewer, knownLabel?)`: `authorDisplayName`(공백 제외) → 작성자가 로그인 사용자면 auth user의 `displayName`, 없으면 `username` → `knownLabel`(이미 서버에서 확인된 이전 표시 이름) → `UNKNOWN_MESSAGE_AUTHOR`.
     - `UNKNOWN_MESSAGE_AUTHOR = '알 수 없는 사용자'`: locale에 unknown user key가 없고, T221은 locale key를 추가하지 않는다(전역 제약 4).
     - `messageAuthorInitials(name)`: 앞뒤 공백을 빼고 code point 기준 앞 두 글자를 대문자로 만든다. 한글과 emoji surrogate pair가 깨지지 않는다. 빈 이름이면 `?`.
   - `shell.ts` 4곳의 `author:`는 `messageAuthorLabel(message, useAuthStore().user)`를 쓴다. Gateway 생성 이벤트는 payload의 `authorDisplayName`을 쓰고, 없으면 같은 규칙을 적용한다. 이때 전송 중(`status: 'sending'`)이 아닌 기존 메시지의 `author`를 `knownLabel`로 넘긴다. 근거 없는 legacy `author` payload key fallback과 `'unknown'` literal은 제거한다.
   - `ChatViewport.vue` avatar는 `messageAuthorInitials(message.author)`를 쓴다. 이름 줄은 이미 `message.author`를 표시하므로 그대로 둔다.

## Expected Changed Files
- Backend main
  - `backend/modules/message/src/main/java/com/example/discord/message/MessageAuthorDirectory.java`(신규 port)
  - `backend/boot/src/main/java/com/example/discord/auth/AuthStore.java`, `InMemoryAuthStore.java`, `JdbcAuthStore.java`(`findProfilesByIds`)
  - `backend/boot/src/main/java/com/example/discord/auth/AuthStoreMessageAuthorDirectory.java`(신규 adapter)
  - `backend/boot/src/main/java/com/example/discord/message/MessageController.java`
  - `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java`
  - `backend/boot/src/main/java/com/example/discord/message/KafkaMessagePublishedConsumer.java`
- Backend test
  - `backend/modules/message/src/test/java/com/example/discord/message/MessageAuthorDirectoryTest.java`(신규)
  - `backend/boot/src/test/java/com/example/discord/auth/AuthStoreMessageAuthorDirectoryTest.java`(신규, in-memory 경로)
  - `backend/boot/src/test/java/com/example/discord/auth/PostgresAuthStoreTest.java`(JDBC 경로, `DISCORD_RUN_POSTGRES_TESTS` gate)
  - `backend/boot/src/test/java/com/example/discord/message/MessageControllerTest.java`(응답 모양)
  - `backend/boot/src/test/java/com/example/discord/message/MessageConfigurationTest.java`(local Gateway payload)
  - `backend/boot/src/test/java/com/example/discord/message/KafkaMessagePublishedConsumerTest.java`(Kafka Gateway payload, 생성자 인자)
  - `backend/boot/src/test/java/com/example/discord/message/MessagePublicationFailureIntegrationTest.java`(생성자 인자)
- 명세·생성물(controller가 `npm run openapi:write`로 재생성해 commit한다. 직접 수정하지 않는다)
  - `docs/api/openapi.json`
  - `packages/api-client/src/generated/**`(`model/messageResponse.ts` 등)
- Web
  - `apps/web/services/message-author.ts`(신규)
  - `apps/web/stores/shell.ts`(`BackendMessageResponse` 타입, mapping 4곳, import)
  - `apps/web/components/shell/ChatViewport.vue`(avatar 이니셜, import)
  - `apps/web/tests/components/message-author.test.ts`(신규)
  - `apps/web/tests/components/shell-contracts.test.ts`(store mapping test 추가)
- 문서
  - 이 blueprint
  - merge 전 evidence `docs/03-analysis/task_T221-merge-review.md`(controller)

## 검증 계획
- 구현자: `cd apps/web && npx vitest run tests/components/message-author.test.ts tests/components/shell-contracts.test.ts`
- controller
  - `npm run lint`
  - `cd apps/web && npx vitest run`
  - `npm run build --workspace @discord-clone/web`
  - `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar`
  - 집중 실행: `.\gradlew.bat :backend:modules:message:test --tests com.example.discord.message.MessageAuthorDirectoryTest`, `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthStoreMessageAuthorDirectoryTest --tests com.example.discord.message.MessageControllerTest --tests com.example.discord.message.MessageConfigurationTest --tests com.example.discord.message.KafkaMessagePublishedConsumerTest --tests com.example.discord.architecture.ArchitectureTest`
  - PostgreSQL 계약(`postgres:16-alpine`, `DISCORD_RUN_POSTGRES_TESTS=true`): `--tests com.example.discord.auth.PostgresAuthStoreTest --tests com.example.discord.message.JdbcMessageStoreTest`
  - Kafka drill이 환경에 있으면 `MessagePublicationFailureIntegrationTest`(생성자 인자 변경 회귀)
  - `npm run openapi:write` 후 생성물 commit, `npm run openapi:check`
  - `git diff --check origin/main HEAD`
  - 운영 build 화면 확인(1440x900, 390x844): 메시지 작성자가 표시 이름으로 보이고 avatar가 이름 이니셜이다.

## 위험
- 응답 시점 이름 해석이므로 page 조회마다 users query가 1회 늘어난다. page당 1회, id 최대 100개로 bounded다.
- `JdbcAuthStore.mapProfile`은 `UserProfile.create`를 쓴다. DB에 빈 `display_name`이나 잘못된 username이 있으면 예외가 난다. 이는 기존 `findById`와 같은 동작이다. 가입 경로가 빈 표시 이름을 막으므로 실제로는 발생하지 않는다.
- Gateway 재전달: Kafka consumer는 같은 event를 현재 상태로 payload를 다시 만든다. 그 사이 표시 이름이 바뀌면 payload hash가 달라질 수 있다. 이 경우 기존 `GatewayEventConflictException` 처리(이미 append됨으로 간주)로 흡수된다. 현재 boot에는 표시 이름 변경 경로가 없다.
- 이미 배포된 client가 `authorDisplayName`을 모르면 무시한다. field 추가라 하위 호환이다.
- web 문구 `알 수 없는 사용자`는 locale을 거치지 않으므로 en-US 화면에서도 한국어로 보인다. locale key 추가는 T222 영역이라 이번에는 literal을 쓴다(brief 지시).
- 전송 중 메시지의 작성자 표시와 mention chip의 id 표시는 남는다(범위 밖, 후속).
- `shell.ts`와 `ChatViewport.vue`는 T220과 같은 file이다. hunk는 mapping 줄과 avatar 줄로 한정했다.
