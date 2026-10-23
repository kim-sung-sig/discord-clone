# T216 메시지 처리 흐름 후속 정리 Blueprint

## Approval Gate
- 상태: 코디네이터 dispatch(`task_670522ee36dd`)로 범위 승인. base `origin/main` `76db98d`.
- 근거
  - PR #13~#14 pre-merge 4차 리뷰의 비차단 항목: spec P2-1·P3-1~P3-5, quality P3-1~P3-7(`C:\tmp\review-extract\premerge4-spec-review.md`, `premerge4-quality-review.md`, 로컬 artifact)
  - `docs/03-analysis/task_kafka-merge-review.md` Residual Risk / Defer의 P3 요약
- 막힌 모호성: 없음. 판단이 필요한 두 항목(retries 집계 의미, `hasAppended`의 `synchronized`)은 아래 "결정과 근거"에 실제 동작 근거와 함께 기록한다.

## Goal
1. JSON `null` message record가 NPE로 3회 시도되지 않고, 재시도 없이 바로 DLQ로 간다.
2. `discord.message.publication.consumer.retries`가 실제로 다시 시도된 전달 수만 센다. 재시도 없는 판정(message topic IAE)과 소진 직전의 마지막 실패는 세지 않는다.
3. `JdbcGatewayEventLog.contains`가 payload를 읽지 않고 존재만 확인한다. `hasAppended`가 Gateway 전역 monitor 밖에서 DB를 조회한다.
4. 동시 전달 conflict 분기와 drill의 metric 단언이 실행 순서에 의존하지 않게 고정된다.
5. 설계·증거 문서가 현재 동작과 최신 drill 수치를 말한다.

## Non-goals
- API 표면, 컨트롤러, `docs/api/openapi.json`, `packages/api-client/src/generated` 변경(T214와 병합 충돌 방지).
- recoverer를 topic별 DLQ(`record.topic() + ".dead-letter"`)로 분리하는 코드 변경. 이번에는 문서화만 한다(후속 과제).
- 동시 중복 전달에서 `processed`가 2회 집계되는 동작 변경(`markProcessed` 삽입 행 수 반환). 문서화만 한다.
- Gateway guild 로드 수(P2-1), OPEN 신고 prune(P3-8), 커서 고착 등 다른 이관 항목.

## Participating Code
- `backend/boot/src/main/java/com/example/discord/message/KafkaMessagePublishedConsumer.java`: `decode`가 `readValue` 결과 `null`을 `IllegalArgumentException("invalid message publication record")`로 바꾼다.
- `backend/boot/src/main/java/com/example/discord/notification/KafkaMessageMentionConsumer.java`: 같은 topic의 두 번째 group(`notification-service-v1`)도 같은 decode 규칙을 쓴다. 이 consumer만 남겨 두면 JSON `null`이 notification group에서 계속 NPE로 3회 시도된다. 그래서 같은 변경을 적용한다(Goal 1의 범위 결정).
- `backend/boot/src/main/java/com/example/discord/message/MessageKafkaConfiguration.java`: 재시도 생략 판정을 한 메서드로 모으고, backoff function과 retry listener가 같은 판정을 쓴다. retry listener는 재시도가 실제로 이어지는 전달만 센다. (P3 후속: retry listener를 없애고 backoff function이 돌려주는 `BackOffExecution`이 대기 시간을 낼 때 센다. 아래 "retries 집계 의미" 참조.)
- `backend/boot/src/main/java/com/example/discord/gateway/JdbcGatewayEventLog.java`: `contains`를 `SELECT 1 FROM gateway_event_log WHERE event_id = ?`로 바꾼다.
- `backend/modules/gateway/src/main/java/com/example/discord/gateway/InMemoryGatewayService.java`: `hasAppended`에서 `synchronized`를 제거한다.

## 결정과 근거
### retries 집계 의미 (Goal 2)
- spring-kafka 4.1.1 `FailedRecordTracker.recovered`는 실패한 전달마다 `RetryListener.failedDelivery(record, exception, deliveryAttempt)`를 먼저 호출하고, 그다음 `BackOffExecution.nextBackOff()`가 `STOP`이면 recoverer로 보낸다. `deliveryAttempt`는 1부터 시작한다(`FailedRecord.deliveryAttempts = new AtomicInteger(1)`).
- 그래서 현재 값은 "실패 전달 콜백 수"다.
  - message topic IAE: backoff `FixedBackOff(0, 0)` → 콜백 1회 후 즉시 DLQ. 재시도는 0회인데 `retries`가 1 오른다.
  - 재시도 가능 실패: 기본 `FixedBackOff(1s, 2)` → 콜백 3회(시도 1·2·3), 실제 재시도는 2회. 3번째 콜백은 소진 직후 DLQ로 가는 전달이다.
- 결정: metric 이름(`retries`)에 맞게 "다시 시도되는 실패 전달"만 센다. 조건은 `message topic` 이고, 재시도 생략 판정이 아니고, `deliveryAttempt <= MAX_RETRIES(2)`이다. 기본 backoff도 같은 상수로 만든다. `resetStateOnExceptionChange`(기본 true)로 예외 종류가 바뀌어 상태가 초기화되면 `deliveryAttempt`도 1부터 다시 시작하고 backoff도 새로 시작하므로 이 조건은 그대로 맞다.
- "실패 전달 콜백"으로 문서화만 하는 안은 채택하지 않았다. 이름과 값이 계속 어긋나고, 운영자가 소진 직전 시도를 재시도로 오해한다.
- 효과: DB 장애 drill의 값이 두 group 합계 6 → 4(group당 재시도 2회)로 바뀐다. drill 단언과 evidence marker 이름(`retry_callbacks` → `retries`)을 같이 바꾼다.

#### P3 후속: retry listener 판정의 경계 2곳 (spec 검토 P3-1)
- `babc6fe`의 retry listener 방식은 backoff 결정을 listener 안에서 다시 계산했다. 그래서 두 경계에서 실제 재시도 수와 어긋났다.
  - ① framework 기본 not-retryable 예외: `FailedRecordProcessor.noRetriesForClassified`가 사용자 backoff function보다 먼저 `FixedBackOff(0, 0)`을 고른다(`MessageConversionException`, `MethodArgumentResolutionException`, `DeserializationException`, `ClassCastException` 등). listener는 이 분류를 몰라 attempt 1을 셌다. 실제 인프라에서 확인했다: tombstone(`null` value) drill이 재시도 없이 DLQ로 가는데 `retries`가 2(group당 1) 올랐다(`C:	mp	216-evidence\p3\drill-poison-before-fix.xml`, 로컬 artifact).
  - ② DLQ 발행 실패 뒤 재전달: recovery가 실패하면 `resetStateOnRecoveryFailure`(기본 true)로 상태가 지워지고 record가 바로 다시 전달된다. 이 재전달은 backoff 대기 없이 일어난다.
- 결정(코드): 집계 위치를 retry listener에서 backoff로 옮겼다. message topic의 재시도 가능 실패에는 backoff function이 `FixedBackOff(1s, 2)`를 감싼 `BackOff`를 돌려준다. 감싼 `BackOffExecution.nextBackOff()`가 `STOP`이 아닌 값(다음 시도까지의 대기)을 낼 때만 `retries`를 1 올린다.
  - framework가 not-retryable로 분류한 예외는 backoff function이 호출되지 않으므로 세지 않는다(①이 정의와 맞게 됨).
  - message topic IAE는 `FixedBackOff(0, 0)`이라 세지 않는다. 재시도 가능 실패는 attempt 1·2에서 대기를 내고 attempt 3에서 `STOP`이므로 2를 센다. `resetStateOnExceptionChange`로 새 backoff가 시작되면 실제로 새 대기가 생기므로 그만큼 센다.
  - `BackOffHandler` wrapper 안은 채택하지 않았다. `onNextBackOff`에는 record가 없어 topic 필터를 container 구독 정보에 기대야 한다. backoff function은 record를 받는다.
- 정의(문서): `retries` = message topic에서 backoff가 다음 시도를 예약한 횟수다. ②의 DLQ 발행 실패 직후 즉시 재전달은 backoff 예약이 아니므로 세지 않는다. 재시도 가능 실패라면 재전달 뒤 새 backoff 주기의 재시도(2회)는 센다. DLQ 발행 장애 자체는 `dead_letter` counter가 오르지 않는 것과 error handler 로그(`Failed to handle …`)로 관측한다. backoff 대기 중 container가 멈추면 예약된 재시도가 실행되지 않을 수 있으나 이 값은 그대로 남는다(종료 경계의 근사).

### causedBy 정리
- 리뷰는 "backoff function이 root cause만 받으므로 `causedBy` 루프가 중복"이라고 했다. 실제 코드를 확인한 결과 사실이 아니다. `FailedRecordTracker.getFailedRecordInstance`가 넘기는 값은 `ErrorHandlingUtils.findRootCause(exception)`인데, 이 메서드는 `ListenerExecutionFailedException`·`TimestampedException` 래퍼만 벗긴다. listener가 던진 예외의 cause 체인은 그대로 남는다. 따라서 `IllegalStateException(cause=IAE)` 같은 경우를 잡으려면 루프가 필요하다(blueprint 계약 "원인 체인 포함").
- 그리고 retry listener에는 래퍼를 벗기지 않은 원래 예외가 전달된다.
- 그래서 루프는 유지하고, 중복은 "판정 자체"를 한 곳(`skipsRetries(record, failure)`)으로 모아 없앤다. backoff function과 retry listener가 같은 판정을 쓴다. (P3 후속: retry listener를 없애 판정을 backoff function 한 곳에서만 하므로 `skipsRetries`를 지우고 `causedBy`를 바로 호출한다.)

### `hasAppended`의 `synchronized` 제거 (Goal 3)
- `hasAppended`는 `eventLog.contains`만 호출하고 service의 가변 상태(session, listener, guild snapshot)를 읽지 않는다.
- `GatewayEventLog` 구현은 두 개뿐이다(`grep "implements GatewayEventLog"`).
  - `InMemoryGatewayEventLog`: 모든 public 메서드가 `synchronized`라 자체로 thread-safe다.
  - `JdbcGatewayEventLog`: 필드는 불변 `DataSource`·`ObjectMapper`뿐이고, 호출마다 connection을 새로 얻는다.
- monitor가 막던 경합(한 JVM에서 다른 delivery의 publish 중간에 `hasAppended`가 false를 보는 경우)은 monitor가 없어도 안전하다. 그 delivery는 `publish`로 가고, 같은 payload면 append dedupe로 같은 event를 반환하며(`notifiedEventIds`로 listener 재알림도 없음), 다른 payload면 `GatewayEventConflictException`을 consumer가 흡수한다. 여러 JVM 사이에서는 원래 monitor가 이 경합을 막지 못했고 같은 장치로 처리한다.
- (P3 후속, 품질 검토 P3-2) monitor 제거로 넓어진 창: `publish`는 `eventBus.publish`(운영 `KafkaGatewayEventBus.publish`의 동기 송신)를 append보다 먼저 한다. 전에는 A의 `publish`가 bus 송신을 기다리는 동안 B의 `hasAppended`도 monitor에서 기다렸다. 이제 B는 그동안 false를 보고 `publish`에 들어가 같은 eventId의 bus record를 한 번 더 보낼 수 있다. 같은 JVM에서 rebalance 경계의 동시 전달일 때 생긴다. 원격 node에서는 append 멱등(같은 payload) 또는 conflict(수정된 payload)로 정리된다. conflict면 원격 gateway bus listener의 DLQ에 `LISTENER_FAILURE` 소음이 남는다. 사용자에게 보이는 중복이나 누락은 없다. 코드 변경은 하지 않았다.
- (P3 후속, spec 검토 P3-2) conflict 분기에서도 같은 순서 때문에 수정된 payload가 bus로 한 번 더 나간다. `InMemoryGatewayService.publish`는 `eventBus.publish` 다음에 `appendBusEvent`에서 conflict를 감지한다. 그래서 durable log와 service listener는 첫 payload만 보지만, bus listener와 다른 node는 수정된 payload를 받는다. base부터 있던 동작이다(premerge4 quality P3-4③). 시험 이름과 단언을 이 동작에 맞췄다(`…WithoutSecondListenerNotification`, bus 송신 2회 단언). 해소안은 잔여 위험에 적었다.
- 이득: 메시지마다 하는 DB 조회 1회가 Gateway 전역 monitor(poll, heartbeat, identify가 공유)를 잡지 않는다.

## Invariants
- message topic의 IAE(원인 체인 포함)는 두 group 모두에서 재시도 없이 DLQ로 간다. 다른 topic listener는 `FixedBackOff(1s, 2회)`를 유지한다.
- JSON `null`, tombstone(`null` value), 깨진 JSON은 모두 같은 metadata-only DLQ로 가고, 같은 partition의 다음 record를 막지 않는다.
- 재시도 가능 실패의 시도 횟수(3회)와 DLQ 시점은 바뀌지 않는다. `failed`·`dead_letter` counter 의미도 바뀌지 않는다.
- `contains`의 의미는 그대로다: 만료 여부와 관계없이 `event_id` 행이 있으면 true.
- Gateway 재발행 생략 조건과 conflict 흡수 범위는 그대로다(`publishToGateway` 안의 `GatewayEventConflictException`만).
- API 표면 변경 없음. 로그·DLQ에 본문을 남기지 않는다.

## Expected Changed Files
- `backend/boot/src/main/java/com/example/discord/message/KafkaMessagePublishedConsumer.java`
- `backend/boot/src/main/java/com/example/discord/message/MessageKafkaConfiguration.java`
- `backend/boot/src/main/java/com/example/discord/notification/KafkaMessageMentionConsumer.java`
- `backend/boot/src/main/java/com/example/discord/gateway/JdbcGatewayEventLog.java`
- `backend/modules/gateway/src/main/java/com/example/discord/gateway/InMemoryGatewayService.java`
- `backend/modules/gateway/src/main/java/com/example/discord/gateway/GatewayEventLog.java`: 구현체가 thread-safe해야 한다는 계약을 Javadoc으로 명시(`hasAppended`가 monitor 밖에서 `contains`를 호출하므로)
- `backend/boot/src/test/java/com/example/discord/message/KafkaMessagePublishedConsumerTest.java`
- `backend/boot/src/test/java/com/example/discord/message/MessageKafkaConfigurationTest.java`
- `backend/boot/src/test/java/com/example/discord/message/MessagePublicationFailureIntegrationTest.java`
- `backend/boot/src/test/java/com/example/discord/notification/KafkaMessageMentionConsumerTest.java`
- `backend/boot/src/test/java/com/example/discord/gateway/PostgresGatewayEventLogContractTest.java`(신규)
- `backend/modules/gateway/src/test/java/com/example/discord/gateway/InMemoryGatewayServiceTest.java`
- `docs/03-analysis/message-publication-resilience-observability.blueprint.md`
- `docs/03-analysis/message-publication-failure-matrix.md`
- `docs/04-report/message-publication-resilience-evidence.json`
- `docs/04-report/message-publication-failure-expansion-evidence.json`
- `docs/04-report/message-publication-resilience-portfolio.html`
- `docs/03-analysis/task_T216-message-pipeline-followups.blueprint.md`(이 문서)
- merge 전 evidence: `docs/03-analysis/task_T216-merge-review.md`(코디네이터 검토 후 별도 commit)
- P3 후속에서 추가: `docs/03-analysis/task_kafka-merge-review.md`(이관 P3 목록에 해소 표시)

## TDD 계획
| # | 테스트 | 종류 | 수정 전 기대 |
|---|---|---|---|
| 1 | `KafkaMessagePublishedConsumerTest.jsonNullRecordIsRejectedAsInvalidWithoutRetryableNpe` | 동작 변경 | RED: `NullPointerException` |
| 1 | `KafkaMessageMentionConsumerTest.jsonNullRecordIsRejectedAsInvalid` | 동작 변경 | RED: `NullPointerException` |
| 2 | `MessageKafkaConfigurationTest.invalidMessagePublicationRecordIsNotCountedAsRetry` | 동작 변경 | RED: `retries` = 1 |
| 2 | `MessageKafkaConfigurationTest.retriesCountOnlyDeliveriesThatAreRetried` | 동작 변경 | RED: `retries` = 3 |
| 2 | `MessageKafkaConfigurationTest.wrappedInvalidRecordStillSkipsRetries` | characterization(원인 체인) | GREEN |
| 3 | `PostgresGatewayEventLogContractTest.containsDoesNotDecodeStoredPayload` | 동작 변경(실제 PG) | RED: payload 해석 실패로 `IllegalStateException` |
| 3 | `PostgresGatewayEventLogContractTest.containsReportsOnlyAppendedEventIds` | characterization(실제 PG) | GREEN |
| 3 | `InMemoryGatewayServiceTest.hasAppendedDoesNotWaitForTheGatewayMonitor` | 동작 변경 | RED: 다른 스레드가 monitor를 잡은 동안 2초 안에 반환하지 못함 |
| 4 | `KafkaMessagePublishedConsumerTest.concurrentAppendConflictCompletesInboxWithoutSecondListenerNotification`(P3 후속에서 `…WithoutSecondGatewayEffect`에서 이름 변경) | characterization(conflict 분기) | GREEN |
| 5 | drill `apiPublicationSurvivesListenerRestartAndDuplicateDelivery`·`databaseConnectionFailure…` | 테스트 견고성 | 운영 동작 변경 없음. `retries` 단언은 2번 변경으로 6 → 4 |

- `containsDoesNotDecodeStoredPayload`는 `payload_json`에 객체가 아닌 JSON(`[]`)을 직접 넣는다. 기존 구현은 `findByEventId`가 payload를 `Map`으로 읽다가 실패하므로, "payload를 읽지 않는다"를 행동으로 관찰할 수 있다.
- `hasAppendedDoesNotWaitForTheGatewayMonitor`는 Gateway listener 안에서 latch로 멈춰 monitor를 잡은 스레드를 만든 뒤, 다른 스레드의 `hasAppended`가 2초 안에 반환하는지 본다.

## Verification Gates
1. 빠른 반복: PowerShell `.\gradlew.bat :backend:boot:test --tests "*KafkaMessagePublishedConsumerTest" --tests "*MessageKafkaConfigurationTest" --tests "*KafkaMessageMentionConsumerTest"`, `.\gradlew.bat :backend:modules:gateway:test`
2. PostgreSQL 계약: 독립 `postgres:16-alpine` 컨테이너(포트 55450 이상), DB 이름 `discord_runtime_guild_t216_*`, `DISCORD_RUN_POSTGRES_TESTS=true`로 `PostgresGatewayEventLogContractTest`, `PostgresGuildStoreContractTest`, `PostgresGuildServiceTest`, `PostgresModerationServiceContractTest`. 끝나면 컨테이너를 제거한다.
3. Kafka drill: `DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS=true`, 로컬 broker `127.0.0.1:29092`, `MessagePublicationFailureIntegrationTest` 전체.
4. 최종: `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar`, `npm run openapi:check`(변경 없음), `git diff --check`.

## Review Score Preset
- Implementation Review: spec 90 이상, quality/security 90 이상, P0/P1 0.

## RED → GREEN 기록
- 실행 환경: Windows PowerShell `.\gradlew.bat`(Gradle 8.14.3). XML 사본은 `C:\tmp\t216-evidence\`(로컬 artifact)에 보존했다.

### RED (운영 코드 수정 전, 테스트만 추가)
명령: `.\gradlew.bat :backend:boot:test --tests "*KafkaMessagePublishedConsumerTest" --tests "*MessageKafkaConfigurationTest" --tests "*KafkaMessageMentionConsumerTest" :backend:modules:gateway:test --tests "*InMemoryGatewayServiceTest" --continue` → boot 34건 중 5건 실패, gateway 22건 중 1건 실패.

| 테스트 | 실패 내용 |
|---|---|
| `KafkaMessagePublishedConsumerTest.jsonNullRecordIsRejectedAsInvalidWithoutRetryableNpe` | `NullPointerException: … "record" is null`(`consume:53`) |
| `KafkaMessageMentionConsumerTest.jsonNullRecordIsRejectedAsInvalid` | `NullPointerException: … "record" is null`(`consume:34`) |
| `MessageKafkaConfigurationTest.invalidMessagePublicationRecordIsNotCountedAsRetry` | `retries` counter가 존재(값 1) |
| `MessageKafkaConfigurationTest.retriesCountOnlyDeliveriesThatAreRetried` | `expected: 2.0 but was: 3.0` |
| `MessageKafkaConfigurationTest.wrappedInvalidRecordStillSkipsRetries` | 원인 체인 IAE의 즉시 DLQ(`handleOne`=true)는 통과했다(characterization). 같은 테스트의 `retries` 단언이 Goal 2 결함으로 실패했다 |
| `InMemoryGatewayServiceTest.hasAppendedDoesNotWaitForTheGatewayMonitor` | `TimeoutException`(monitor를 잡은 publish가 풀릴 때까지 `hasAppended`가 대기) |
| `KafkaMessagePublishedConsumerTest.concurrentAppendConflictCompletesInboxWithoutSecondGatewayEffect`(현재 이름 `…WithoutSecondListenerNotification`) | 통과(기존 동작 characterization. conflict 분기를 처음으로 직접 실행) |

PostgreSQL RED: `JdbcGatewayEventLog.java`만 `HEAD` 버전으로 임시 복원하고, 독립 `postgres:16-alpine`(127.0.0.1:55461, DB `discord_runtime_guild_t216_red`)에서 `PostgresGatewayEventLogContractTest` 실행 → 2건 중 1건 실패. `containsDoesNotDecodeStoredPayload`: `IllegalStateException` ← `SQLException` ← `MismatchedInputException`(payload `[]`를 `Map`으로 읽다 실패). `containsReportsOnlyAppendedEventIds`는 통과(characterization). 그 뒤 수정 버전을 복원했다.

### GREEN
- 같은 focused 명령(+`MessageKafkaRecoveryFailureTest`, gateway 모듈 전체): `BUILD SUCCESSFUL`. XML: `KafkaMessagePublishedConsumerTest` 8, `MessageKafkaConfigurationTest` 22, `MessageKafkaRecoveryFailureTest` 4, `KafkaMessageMentionConsumerTest` 3, `InMemoryGatewayServiceTest` 22, `InMemoryGatewayEventLogTest` 4, `InMemoryGatewayServiceGuildReadTest` 3. 실패·skip 0.
- PostgreSQL 계약(DB `discord_runtime_guild_t216_green`, `DISCORD_RUN_POSTGRES_TESTS=true`, `--rerun`): `PostgresGatewayEventLogContractTest` 2, `PostgresGuildStoreContractTest` 7, `PostgresGuildServiceTest` 6, `PostgresModerationServiceContractTest` 12, `GuildRuntimeConfigurationTest` 4, `ModerationRuntimeConfigurationTest` 3. 실패·skip 0. 실행 후 컨테이너 `t216-pg-contract`를 제거했다.
- Kafka drill(`DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS=true`, broker `127.0.0.1:29092`, `--rerun`): `MessagePublicationFailureIntegrationTest` 11/11, 실패·skip 0, 78.818초(XML timestamp `2026-10-23T23:07:30.977Z`).
  - `db_failure_attempts=3 retries=4 dlq_records=2 dlq_after_failure_ms=13876 recovery_to_inbox_ms=195 end_to_end_samples=1 end_to_end_total_ms=14438.221 outbox_backlog=0`
  - `duplicate_events=1 end_to_end_samples=1 end_to_end_total_ms=265.844`(시나리오 전후 차이)
  - `consumer_lag_peak=5 consumer_lag_drained=0 queued_events=5`
  - `jsonNullDeadLettersAndDoesNotBlockTheNextRecord` 4.435초. 이전 drill XML 6개(`C:\tmp\review-extract\*-kafka-drill*.xml`)는 7.132~7.249초였다. 깨진 JSON·tombstone(4.5~4.7초)과 같아졌으므로, gateway·notification 두 group의 NPE 재시도(group마다 1초 backoff 2회, 두 group은 병렬로 진행)가 사라졌다는 실제 인프라 증거다. (P3 후속: 이 근거는 시간 비교뿐이었다. 지금은 drill이 재시도 0과 group별 실패 전달 1회를 직접 단언한다. 아래 "P3 후속" 절.)
- drill의 duplicate 단언 변경(5번)은 운영 동작 변경이 아니라 RED가 없다. 기존 실패 원인(다른 시나리오의 `duplicate` 누적)은 premerge3 drill XML(`expected: 1.0 but was: 2.0`)로 이미 기록돼 있다. end-to-end sample은 다른 테스트가 남긴 outbox 행이 함께 relay될 수 있어 `== 1`이 아니라 양수로 단언하고, 출력 key를 `end_to_end_total_ms`(시나리오 합계)로 바꿨다.

### 최종 검증 (최종 내용 기준)
- `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar --continue` → `BUILD SUCCESSFUL`, 종료 코드 0. 전체 XML 합계 637건, 실패·오류 0, skip 122(opt-in PostgreSQL·Kafka 시험은 환경 변수 없이 skip). `GatewayEventLog` Javadoc 추가 뒤 같은 명령을 다시 실행해 통과했다.
- `npm run openapi:check` → `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=162`. `docs/api/openapi.json`, `packages/api-client/src/generated`, 컨트롤러는 변경하지 않았다.
- `git diff --check` 통과.

## 문서 변경 요약
- `message-publication-resilience-observability.blueprint.md`: "inbox만 완료하고 `duplicate`로 센다" 모순 문장 정정과 경위, 공유 recoverer가 authz listener 소진 record를 message DLQ로 보내는 점, `retries`·`processed` 의미(T216 정정 절).
- `message-publication-failure-matrix.md`: 2026-10-22 수치에 시점 표기, T216 이후 수치와 정정 3건.
- `message-publication-resilience-evidence.json`: 최신 drill(11건)로 재생성. 2026-10-21 최초 값은 `previousSnapshots`에 보존하고 당시 retry callback 6이 현재 정의로 4에 해당함을 적었다. 측정 한계 문구(강제 종료 검증됨, processed 집계, recoverer)를 갱신했다.
- `message-publication-failure-expansion-evidence.json`: `snapshotNote`로 2026-10-22 시점 값임을 표기.
- `message-publication-resilience-portfolio.html`: IAE 범위 정정 각주, 잔여 리스크의 retries 정의·recoverer, 실행 근거 문장, 새 절 "2026-10-24 T216 후속 정리와 최신 실측".

## P3 후속 (2026-10-24, `babc6fe` 검토 반영)
- 근거: `babc6fe` 독립 검토 2건(spec 96/100, 품질·보안 93/100, P0~P2 없음)의 P3. 보고서는 `C:	mp
eview-extract	216-spec-review.md`(P3-1~3), `t216-quality-review.md`(P3-1~5)(로컬 artifact).

| 지적 | 처리 |
|---|---|
| drill JSON `null`의 "재시도 없음"이 시간 비교뿐(spec P3-3, 품질 P3-1) | 반영. `assertPoisonDoesNotBlockNextRecord`가 poison 3종 모두에서 `retries` 시나리오 차이 0과 group별 실패 전달 1회(listener interceptor의 failure 신호, `FAILED_DELIVERIES`)를 단언한다. 다음 record 처리와 DLQ 2건을 확인한 뒤에 본다. |
| retries 경계 2곳(spec P3-1) | ① 코드 반영: 집계를 backoff로 옮겨 framework 기본 not-retryable 예외를 세지 않는다. ② 문서화: DLQ 발행 실패 뒤 즉시 재전달은 정의 밖이다. 위 "retries 집계 의미" 절과 observability blueprint. |
| conflict 분기 시험 이름(spec P3-2, 품질 P3-2) | 반영. 이름을 `…WithoutSecondListenerNotification`으로 바꾸고, bus 송신 2회(첫 payload, 수정된 payload)와 durable log의 첫 payload를 단언한다. 경합 분석과 잔여 위험에 bus 중복 송신 창을 추가했다. |
| portfolio 잔여 리스크 첫 항목(품질 P3-3) | 반영. 2026-10-21 기준 문장임과 2026-10-22 JVM 강제 종료 검증을 각주로 적었다. broker 중단은 미검증으로 남겼다. |
| evidence JSON `source`(품질 P3-4) | 반영. `source`에 덮어쓰기 위험을 적고, 보존 사본 `sourceCopy`(`C:	mp	216-evidence\drill-green.xml`)를 추가했다. 후속 drill은 `followUpRuns`에 따로 기록했다. |
| 이관 P3 목록과 blueprint 문구(품질 P3-5) | 반영. `task_kafka-merge-review.md` P3 요약에 해소 표시를 붙이고, 4.4초 근거 문구를 "두 group"으로 고쳤다. |

### RED → GREEN
- 단위 RED(운영 코드 수정 전, 시험만 추가): `.\gradlew.bat :backend:boot:test --tests "*MessageKafkaConfigurationTest" --tests "*KafkaMessagePublishedConsumerTest" --continue` → 33건 중 1건 실패.
  - `MessageKafkaConfigurationTest.frameworkNotRetryableFailureIsNotCountedAsRetry`: `MessageConversionException`이 즉시 DLQ(`handleOne`=true)인데 `retries` counter가 생김(값 1). 동작 변경 RED.
  - `MessageKafkaConfigurationTest.deadLetterPublishFailureRedeliveryIsNotCountedAsRetry`: 통과. IAE는 전에도 세지 않았다. 경계 ②의 정의를 고정하는 characterization이다.
  - `KafkaMessagePublishedConsumerTest.concurrentAppendConflictCompletesInboxWithoutSecondListenerNotification`: 통과. bus 송신 2회는 base부터 있던 동작이다(characterization).
  - XML: `C:	mp	216-evidence\p3
ed-config.xml`, `red-consumer.xml`.
- drill RED 1(운영 코드 수정 전, `--tests "*MessagePublicationFailureIntegrationTest.*DeadLettersAndDoesNotBlockTheNextRecord" --rerun`): 3건 중 tombstone 1건 실패. `[poison record retries across both groups] expected: 0.0 but was: 2.0`. 경계 ①을 실제 인프라에서 재현했다. 깨진 JSON(4.503초)·JSON `null`(4.495초)은 통과. XML `drill-poison-before-fix.xml`.
- drill RED 2(JSON `null` 단언이 회귀를 잡는지 확인): 집계 수정 뒤 두 consumer의 `record == null` 검사만 임시로 끄고 `jsonNullDeadLetters…`를 실행 → `expected: 0.0 but was: 4.0`(group당 NPE 재시도 2회), 10.799초. 검사를 원래대로 되돌렸다(`git status`로 확인). XML `drill-jsonnull-red-probe.xml`.
- GREEN
  - 같은 단위 명령 + `MessageKafkaRecoveryFailureTest`, `KafkaMessageMentionConsumerTest`: Configuration 24, Consumer 8, RecoveryFailure 4, Mention 3, 실패·skip 0.
  - Kafka drill 전체(`DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS=true`, broker `127.0.0.1:29092`, `--rerun`): 11/11, 실패·skip 0, 74.946초(timestamp `2026-10-23T23:32:29.436Z`). poison 3종 4.427~4.480초. `db_failure_attempts=3 retries=4 dlq_records=2 dlq_after_failure_ms=13809 recovery_to_inbox_ms=172`, `duplicate_events=1`, `consumer_lag_peak=5 consumer_lag_drained=0`. backoff 기반 집계도 DB 장애에서 4로 같다. XML `drill-full-green.xml`.
- 최종: `.\gradlew.bat test checkstyleMain checkstyleTest --continue` → `BUILD SUCCESSFUL`, 종료 코드 0. 전체 XML 합계 639건(기존 637 + 새 단위 시험 2), 실패·오류 0, skip 122(opt-in PostgreSQL·Kafka 시험). 로그 `C:	mp	216-evidence\p3ull-verify.log`. `git diff --check` 통과. 컨트롤러, `docs/api/openapi.json`, 생성 client는 바꾸지 않았다.

## 잔여 위험 / 후속
- topic별 DLQ 분리(공유 recoverer)는 문서화만 했다.
- 동시 중복 전달의 `processed` 2회 집계는 문서화만 했다(`markProcessed` 삽입 행 수 반환은 후속).
- (P3 후속 추가) conflict 감지 전에 bus로 나가는 payload(premerge4 quality P3-4③): `InMemoryGatewayService.publish`가 `eventBus.publish`를 append보다 먼저 하므로, 동시 전달이나 수정 뒤 재전달에서 수정된 payload가 `gateway.events` topic으로 다른 node에 나간 뒤 conflict가 흡수된다. `hasAppended` monitor 제거로 같은 JVM에서도 이 창이 넓어졌다(위 "결정과 근거"). 원격 append 멱등·conflict로 정리되므로 사용자에게 보이는 중복·누락은 없고, 원격 bus DLQ 소음만 남는다. 후속 해소안: append 성공 뒤 bus publish로 순서를 바꾸거나, bus publish 전에 같은 eventId의 payload hash를 확인한다.
- Wiki 반영(코디네이터 수용 후): `wiki/Backend Architecture.md`의 2026-10-21 message publication 절에 `retries` 정의와 JSON `null` 분류를, `log.md`에 T216 항목을 추가한다.
