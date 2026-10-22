# T212 Moderation lock 경합 제거 Blueprint

## Approval Gate
- 상태: 승인됨. 사용자가 2026-10-22 "Moderation lock 경합 제거 먼저 진행"으로 승인했다.
- 근거
  - PR #13 merge-review `docs/03-analysis/task_kafka-merge-review.md`의 잔여 P2: guild 행 `FOR UPDATE` vs `FOR SHARE` 경합
  - Pre-merge 품질 리뷰 P2-2(`C:\tmp\review-extract\premerge-quality-review.md`)
- 막힌 모호성: 없음.
- 경쟁 안: (a) 아래 방안, (b) moderation을 별도 lock 행으로 분리(예: `moderation_guild_locks`). (b)는 migration이 필요하고 guild 삭제 cascade와의 관계가 늘어나 채택하지 않았다.

## Goal
- 규칙에 걸리지 않는 메시지의 `evaluateMessage`가 guild 행 lock을 잡지 않는다. 이 경로는 메시지 발행 hot path다.
- 권한 판정에 쓰는 `JdbcGuildStore.get`이 guild 행에 `FOR SHARE`를 잡지 않으면서도, 여러 테이블을 읽는 동안 일관된 snapshot을 유지한다.
- 차단(AutoMod block) 경로는 지금처럼 guild lock 아래에서 alert·audit·retention prune을 원자적으로 기록한다.

## Non-goals
- moderation retention(guild당 500건) 정책 변경. 별도 후속(OPEN 신고 보호)으로 남긴다.
- `JdbcGuildStore.update`의 `FOR UPDATE` 쓰기 직렬화 변경.
- Gateway snapshot 캐시나 projection 경로 변경.

## Participating Code
- `backend/boot/src/main/java/com/example/discord/moderation/JdbcModerationService.java`
  - `evaluateMessage`: guild 존재 확인과 활성 규칙 조회를 lock 없이 한다. 일치하는 규칙이 있을 때만 `write`(guild `FOR UPDATE`)로 들어간다. lock 아래에서 규칙을 다시 읽어 첫 번째로 일치하는 규칙으로 차단을 확정한다. 그 사이 규칙이 비활성화됐으면 허용한다.
- `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildStore.java`
  - `get`: `REPEATABLE READ` + `READ ONLY` 트랜잭션으로 `loadGuild`를 실행하고 행 lock은 잡지 않는다. 끝나면 connection의 isolation과 read-only 상태를 원래대로 되돌린다.
  - `update`: 지금처럼 `READ COMMITTED` + `FOR UPDATE`를 유지한다.

## Invariants
- 규칙에 걸리지 않으면 쓰기가 0건이다. alert·audit 행이 생기지 않는다.
- 차단되면 기존과 같다: alert 1건, audit 1건, retention prune이 guild lock 아래 한 트랜잭션에서 일어나고, 실패하면 rollback된다.
- 존재하지 않는 guild는 규칙 일치 여부와 관계없이 `IllegalArgumentException("guild not found")`를 던진다.
- 결정은 lock을 잡은 시점의 활성 규칙 기준이다. lock 없이 읽은 결과는 차단 후보를 고르는 데만 쓴다.
- `JdbcGuildStore.get`은 roles·members·channels·overwrites를 하나의 snapshot에서 읽는다. 다른 트랜잭션이 guild 행 `FOR UPDATE`를 잡고 있어도 기다리지 않고, 이미 commit된 상태를 반환한다.
- PostgreSQL에서 `REPEATABLE READ READ ONLY` 트랜잭션은 serialization failure를 일으키지 않는다(쓰기가 없으므로). 그래서 재시도 로직을 추가하지 않는다.

## Expected Changed Files
- `backend/boot/src/main/java/com/example/discord/moderation/JdbcModerationService.java`
- `backend/boot/src/main/java/com/example/discord/guild/JdbcGuildStore.java`
- `backend/boot/src/test/java/com/example/discord/moderation/PostgresModerationServiceContractTest.java`
- `backend/boot/src/test/java/com/example/discord/guild/PostgresGuildStoreContractTest.java`
- `docs/03-analysis/task_T212-moderation-lock-contention.blueprint.md`(이 문서)
- `docs/01-plan/features/T213-springdoc-orval-api-client.plan.md`: 사용자 요청으로 남기는 다음 task 계획. 이 task에서는 구현하지 않는다.
- merge 전 evidence: `docs/03-analysis/task_T212-merge-review.md`(별도 commit)

## TDD와 Verification Gates
1. RED(실제 PostgreSQL, `DISCORD_RUN_POSTGRES_TESTS=true`)
   - `evaluateMessage`가 규칙에 걸리지 않는 메시지에서 guild 행을 `FOR UPDATE`로 잡은 다른 트랜잭션을 기다리지 않는다. 2초 안에 허용을 반환해야 한다.
   - 차단 경로는 guild lock을 기다린다. `pg_stat_activity` `wait_event_type='Lock'`을 관측하고, lock이 풀리면 alert·audit를 기록한다.
   - lock 대기 중 규칙이 비활성화되면 차단하지 않는다.
   - 존재하지 않는 guild는 규칙이 없어도 `guild not found`다.
   - `JdbcGuildStore.get`이 guild 행 `FOR UPDATE`를 잡은 트랜잭션을 기다리지 않고, commit 전 상태를 반환한다.
   - `JdbcGuildStore.get`은 `REPEATABLE READ` + read-only로 실행되고 connection 상태를 복원한다. connection proxy로 확인한다.
2. GREEN 후 회귀
   - PostgreSQL 계약 전체(`PostgresGuildServiceTest`, `PostgresGuildStoreContractTest`, `PostgresModerationServiceContractTest`, configuration 테스트)
   - `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar`, `npm run lint:backend`, migration guard, `git diff --check`
3. 리뷰: Orca orchestration fresh Claude 리뷰어로 spec compliance와 code quality/security를 각각 90 이상, P0/P1 0까지 반복한다.

## Review Score Preset
- Implementation Review: spec 90 이상, quality/security 90 이상, P0/P1 0.

## 구현 결과
- RED(수정 전, 독립 `postgres:16-alpine` DB `discord_runtime_guild_t212_red`): 32건 중 3건 실패.
  - `allowedMessageDoesNotWaitForTheGuildRowLock`: `TimeoutException`. 규칙에 걸리지 않는 메시지도 guild lock을 기다렸다.
  - `readDoesNotWaitForAConcurrentGuildWriterAndSeesOnlyCommittedState`: `TimeoutException`. `FOR SHARE`가 writer를 기다렸다.
  - `readUsesARepeatableReadReadOnlySnapshotAndRestoresTheConnection`: isolation·read-only 설정이 없었다.
  - 차단 대기, lock 대기 중 규칙 비활성화, guild 미존재 테스트는 기존 동작을 고정하는 회귀 가드로 수정 전에도 통과했다.
- GREEN(`discord_runtime_guild_t212_green`): GuildRuntimeConfiguration 4, PostgresGuildService 6, PostgresGuildStoreContract 7, ModerationRuntimeConfiguration 3, PostgresModerationServiceContract 12. 실패/skip 0.
