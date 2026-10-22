# Runtime Persistence Remediation Blueprint

## Approval Gate
- Status: Approved
- Approver: User requested completion of the three identified persistence gaps.
- Blocking ambiguity: None. In-memory adapters remain available for explicit test profiles only; runtime persistence for these paths uses PostgreSQL.
- Competing plan branches: Reuse the repository's previously implemented PostgreSQL GuildStore and ModerationService patterns rather than inventing new adapters.

## Goal
- Kafka-backed message publication must not start without PostgreSQL inbox/outbox persistence.
- Guild runtime state must be read from PostgreSQL per operation, without a process-wide inherited map or startup `loadAll()`.
- Moderation state must use PostgreSQL in runtime profiles and remain in-memory only in test profiles.

## Non-goals
- Removing in-memory test fakes from domain modules.
- Migrating unrelated runtime services or changing HTTP contracts.
- Reworking the existing Kafka retry/DLQ behavior.

## Participating Code
- `RuntimeResourceProfileGuard`: reject `kafka` without `postgres`, except explicit `test` profile.
- `MessageKafkaConfiguration` / `MessageConfiguration`: keep test fallback wiring; production Kafka uses existing JDBC Inbox and Outbox.
- `GuildService` / `GuildStore` / `JdbcGuildStore`: stateless service and transaction-owned PostgreSQL state.
- `InMemoryGuildService`: test adapter only.
- `ModerationService` / `JdbcModerationService`: JDBC runtime adapter; `InMemoryModerationService` is test-only.
- Boot callers: depend on service contracts, not concrete in-memory classes.

## Expected Changed Files
- 이 절은 2026-10-22 commit 범위를 고정하기 위해 추가했다. 아래 파일 외 Guild/Moderation 파일은 `docs/superpowers/plans/2026-10-14-runtime-persistence-guild.md`, `docs/superpowers/plans/2026-10-14-runtime-persistence-moderation.md`의 Expected Changed Files를 따른다.
- Kafka/PostgreSQL profile guard: `backend/boot/src/main/java/com/example/discord/ops/RuntimeResourceProfileGuard.java`, `backend/boot/src/test/java/com/example/discord/ops/RuntimeResourceProfileGuardTest.java`, `backend/boot/src/main/java/com/example/discord/message/MessageKafkaConfiguration.java`, `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java`, `backend/boot/src/test/java/com/example/discord/message/MessageConfigurationTest.java`, `backend/boot/src/test/java/com/example/discord/message/MessageControllerTest.java`.
- `GuildService` 계약으로 전환한 나머지 boot caller: `backend/boot/src/main/java/com/example/discord/event/ServerEventController.java`, `backend/boot/src/main/java/com/example/discord/event/ServerEventRecipientResolver.java`, `backend/boot/src/main/java/com/example/discord/notification/NotificationController.java`, `backend/boot/src/main/java/com/example/discord/notification/NotificationProjectionConfiguration.java`, `backend/boot/src/main/java/com/example/discord/notification/MessageMentionRecipientResolver.java`.
- 삭제: `backend/boot/src/main/java/com/example/discord/guild/PersistentGuildService.java`, `GuildSnapshotStore.java`, `JdbcGuildSnapshotStore.java`(startup snapshot hydration 제거).
- Guild wiring/test: `backend/boot/src/test/java/com/example/discord/guild/GuildRuntimeConfigurationTest.java`, `backend/modules/guild/src/test/java/com/example/discord/guild/GuildServicePermissionSnapshotTest.java`.
- 2026-10-22 리뷰 반영: `backend/boot/src/main/java/com/example/discord/message/MessagePublicationSchedulingConfiguration.java`, `backend/boot/src/test/java/com/example/discord/message/MessagePublicationSchedulingConfigurationTest.java`, `backend/boot/src/test/java/com/example/discord/event/ServerEventRecipientResolverTest.java`, `backend/boot/src/test/java/com/example/discord/notification/MessageMentionRecipientResolverTest.java`, `docs/04-report/2026-10-22-kafka-runtime-code-review.html`.
- 이 blueprint 자체: `docs/03-analysis/runtime-persistence-remediation.blueprint.md`.

## Invariants
- Production-like profiles continue to require `postgres`.
- Kafka without PostgreSQL fails during profile validation before any message can be accepted or discarded by a no-op outbox.
- Test profile may use in-memory adapters for infrastructure-free tests.
- Guild writes lock one guild row and commit aggregate rows plus authorization projection changes in one transaction; reads do not populate process-wide state.
- Moderation writes and associated audit/alert rows share a transaction; bounded history and existing page limits remain unchanged.

## Verification Gates
- TDD RED/GREEN for Kafka profile composition and runtime adapter selection.
- Guild and moderation focused tests plus opt-in PostgreSQL contract tests against a task-scoped database.
- Backend tests, Checkstyle, boot JAR, backend lint, migration guard, and `git diff --check`.
- Inspect production bytecode/source wiring to ensure no runtime main path injects in-memory Guild or Moderation adapters.

## Review
- Reuse the previously accepted implementation plans and evidence on `task_RUNTIME-persistence` for Guild and Moderation as implementation references; re-run verification on this branch before acceptance.

## Blueprint Alignment
- Matches blueprint: yes.
- Mismatch: None. Runtime callers now use `GuildService`/`ModerationService`; durable profiles select JDBC adapters; explicit `test` profiles select in-memory adapters.
- Correct owner of fix: implementation.

## Verification Evidence
- RED/GREEN: `RuntimeResourceProfileGuardTest.kafkaRuntimeProfileRequiresPostgresProfile` failed before the guard change and passed afterward. `test,kafka` remains allowed for test fakes.
- Current branch PostgreSQL contracts: `PostgresGuildStoreContractTest` 5, `PostgresGuildServiceTest` 6, `PostgresModerationServiceContractTest` 8; all failures/errors/skips 0 on task DB `discord_runtime_guild_20261021_remediation`.
- Runtime wiring: Guild and Moderation configuration tests 3 each; all passed. Kafka/PostgreSQL failure integration passed 4/4.
- Regression: `test checkstyleMain checkstyleTest :backend:boot:bootJar`, `npm run lint:backend`, migration guard, and `git diff --check` passed.
- The first attempt against the existing `discord_runtime_guild_20261014` database stopped at Flyway checksum validation for V20 before migration or test mutations. A fresh task database was used for the successful run.

## Implementation Review
- Preset: Implementation Review.
- Score: 94/100 (blueprint alignment 20/20, correctness 19/20, security/privacy 14/15, tests/verification 19/20, maintainability 14/15, operations/docs 8/10).
- P0/P1: none found in source and contract review.
- Residual P2: guild channel overwrite reads are N+1; moderation mutations/evaluation serialize on a guild row lock. Measure before optimizing.
- Limits: actual process restart/deployment and production throughput were not tested. This slice does not claim all backend in-memory implementations are gone.
- Required loop action: no implementation fix. Publish the accepted runtime/profile and verification facts to the wiki.
- Accepted blueprint: yes. Accepted implementation: yes for this slice. Accepted verification: yes.

## 2026-10-22 코드 리뷰 반영
- 근거: `docs/04-report/2026-10-22-kafka-runtime-code-review.html` P2-2, P2-3.
- P2-2 수정: `GuildConfiguration.inMemoryGuildService`를 `@Profile("test & !postgres")`로 제한했다. `test,postgres` 조합에서 JDBC `GuildService` 하나만 등록된다(`ModerationConfiguration`의 test fake와 같은 조건). RED/GREEN: `GuildRuntimeConfigurationTest.testAndPostgresProfilesSelectOnlyDatabaseGuildService`.
- P2-3 수정: `GuildService.channelViewers(Guild, UUID, Collection<UUID>)`를 추가해 하나의 guild snapshot에서 채널 열람 권한을 일괄 판정한다. `ServerEventRecipientResolver`와 `MessageMentionRecipientResolver`는 fan-out마다 guild를 한 번만 읽는다(기존: 수신자마다 `canViewChannel`이 guild 전체를 재조회). 멘션이 없거나 author 자신만 멘션한 메시지는 guild를 읽지 않는다. guild가 없으면 기존처럼 `IllegalArgumentException("guild not found")`이 전파된다. RED/GREEN: `GuildServicePermissionSnapshotTest.channelViewersAreDecidedFromTheGivenSnapshotWithoutRereading`, `ServerEventRecipientResolverTest`, `MessageMentionRecipientResolverTest.everyoneMentionReadsTheGuildOnceRegardlessOfMemberCount`(멤버 50명에서 read 1회).
- 잔여: 대형 guild의 실제 JDBC 쿼리 수·지연 측정은 수행하지 않았다. snapshot 1회 조회 자체의 비용(멤버·채널·overwrite 전체 로드)은 남는다.
