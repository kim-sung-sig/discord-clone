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
  - `get`: 트랜잭션 첫 문장으로 `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY`를 실행한 뒤 `loadGuild`를 실행하고 행 lock은 잡지 않는다. 설정이 트랜잭션 범위라서 session 상태를 바꾸거나 되돌릴 필요가 없다(round trip 1회).
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
   - `JdbcGuildStore.get`은 첫 query가 잡은 snapshot으로 모든 테이블을 읽는다. guild 행을 읽은 뒤 commit된 channel이 결과에 보이지 않아야 한다(행동 테스트).
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

## 1차 리뷰 반영 (`c21f01b` 대상)
- 1차 리뷰 결과: spec 94/100, quality/security 92/100, P0/P1/P2 0. Orca run `run_181f315a6084`.
- P3 반영
  - session isolation 변경(`SHOW` 1회 + `SET SESSION` 2회)을 트랜잭션 범위 `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY` 1회로 바꿨다. 이로써 restore 경로, commit 후 restore 실패로 결과를 버리는 문제, isolation 기본값 가정이 모두 없어졌다.
  - moderation read 로그에 `operation` 태그를 넣고, 거부되면 `moderation_read_rejected`를 남긴다.
  - 테스트 helper `lockGuild`가 실패할 때 connection을 닫는다.
  - T213 문서의 경로 수를 정정했다.
- 행동 테스트 교체: `readLoadsEveryTableFromTheSnapshotTakenByItsFirstQuery`. guild 행 query 다음, roles query 직전에 다른 connection으로 channel을 commit한다. 그 channel이 결과에 없어야 한다. 구현을 `READ COMMITTED, READ ONLY`로 바꾼 mutant에서는 실패했고(유효성 확인), `REPEATABLE READ`로 되돌리면 통과한다.
- GREEN(`discord_runtime_guild_t212_green2`): PG 계약과 configuration 32건, 실패/skip 0.
