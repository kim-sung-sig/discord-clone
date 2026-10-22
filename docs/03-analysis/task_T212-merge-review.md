# T212 Merge Review

## 대상

- Task branch: `task_T212-moderation-lock-contention`
- Base `origin/main` SHA: `9b2c38bdab0537b5f53574d3cef6d2a178cf20d0`
- Reviewed content SHA: `ff0853400b9e6459f602f232a7e2c869bf5b16c2`
  - `c21f01b` 구현: 허용 경로 lock 제거, guild read snapshot, T213 계획 문서
  - `ff08534` 1차 리뷰 P3 반영: 트랜잭션 범위 `SET TRANSACTION`, 로그 operation 태그, 행동 snapshot 테스트
- Evidence artifact: 이 문서만 변경하는 reviewed content의 자식 commit이다.
- Blueprint: `docs/03-analysis/task_T212-moderation-lock-contention.blueprint.md`

## 독립 리뷰

Orca orchestration으로 띄운 fresh Claude 리뷰어가 읽기 전용으로 검수했다(run `run_181f315a6084`).

| 차수 | 구분 | Preset | 대상 SHA | 점수 | P0/P1/P2 | 판정 |
| --- | --- | --- | --- | ---: | --- | --- |
| 1 | Spec compliance | Implementation Review | `c21f01b` | 94/100 | 0/0/0 | 통과(P3 5) |
| 1 | Quality/Security | Implementation + Security Review | `c21f01b` | 92/100 | 0/0/0 | 통과(P3 8, 일부 반영) |
| 2 | Spec compliance | Implementation Review | `ff0853400b9e6459f602f232a7e2c869bf5b16c2` | 95/100 | 0/0/0 | **승인** |
| 2 | Quality/Security | Implementation + Security Review | `ff0853400b9e6459f602f232a7e2c869bf5b16c2` | 95/100 | 0/0/0 | **승인** |

- 리뷰 원문은 로컬 artifact로 남아 있다: `C:\tmp\review-extract\t212-*-review.md`, `t212-*-review2.md`.

## Verification (`ff0853400b9e6459f602f232a7e2c869bf5b16c2`)

| 명령 | 결과 |
| --- | --- |
| `.\gradlew.bat --version` | Gradle 8.14.3 |
| `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar` | 성공 |
| `npm run lint:backend` | 성공 |
| `qa/migration-guard.contract.ps1` | `MIGRATION_GUARD_PASS` |
| `DISCORD_RUN_POSTGRES_TESTS=true` boot:test `--rerun`(독립 `postgres:16-alpine`, 실행 후 제거) | GuildRuntimeConfiguration 4, PostgresGuildService 6, PostgresGuildStoreContract 7, ModerationRuntimeConfiguration 3, PostgresModerationServiceContract 12. 실패/skip 0 |
| `DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS=true` boot:test `--rerun` | Kafka/PG drill 46건, 실패/skip 0. relay는 `scheduling-1`, lag 조회는 `message-publication-lag-1`에서 실행 |
| `git diff --check 9b2c38b ff08534` | 성공 |
| RED | 수정 전 3건 실패(lock 대기 `TimeoutException` 2건, snapshot 미적용 1건). 행동 snapshot 테스트는 `READ COMMITTED` mutant에서 실패한다 |

## Blueprint Alignment

- Matches blueprint: yes. 변경 파일은 Expected Changed Files 6개와 이 evidence 문서다.
- Mismatch(문서 전용 P3)
  - T213 계획의 "path builder 약 35개"는 리뷰어 집계로 48개다.
  - blueprint의 RED 절에 교체 전 테스트 이름(`readUsesARepeatableReadReadOnlySnapshot…`)이 기록으로 남아 있다.
  - 테스트는 writer를 `FOR UPDATE`가 아니라 `UPDATE`로 흉내 낸다. 행 lock 효과는 같다.
- Correct owner of fix: 문서 정정은 T213 착수 시 함께 한다.

## Acceptance

- Accepted blueprint: yes
- Accepted implementation: yes
- Accepted verification: reviewed content 기준 yes
- Publishable knowledge
  - 메시지 AutoMod 허용 경로는 lock 없이 판정하고, 차단은 lock 아래에서 다시 확인한다.
  - guild read는 트랜잭션 범위 `REPEATABLE READ, READ ONLY` snapshot을 쓴다(`FOR SHARE` 없음).

## Residual Risk / Defer

- P3
  - `READ ONLY`를 단독으로 고정하는 테스트가 없다.
  - 허용 경로 precheck 로그가 `rows=1`로 남는다.
  - 테스트의 lock polling helper가 중복된다.
- 대형 guild의 snapshot 1회 로드 비용(쿼리 `5 + 채널 수`)은 그대로다.
- moderation retention 500건 정책 때문에 OPEN 신고가 밀려날 수 있다(별도 후속).
- 다음 task: T213 springdoc + orval(`docs/01-plan/features/T213-springdoc-orval-api-client.plan.md`).
