# task_kafka Merge Review

## 대상

- Task branch: `task_kafka-resilience-observability` (규칙의 `task_<id>-<slug>`에서 id=`kafka`)
- Base `origin/main` SHA: `661a6a20c9a2c9934e6d70796db2658b6c629ce4`
- Reviewed content SHA: `78812dbcd5a2ebea6da2fdadbf84ee24dbdbe83c`
  - `be1c9a9` 기능: Kafka 발행 복원력·관측, runtime persistence 마감, 코드 리뷰 P2 3건 반영
  - `0e793b7` pre-merge 리뷰 수정: Gateway hot path guild 재조회 제한, 재전달 conflict, 재시도 분류 범위, TopicExists 경합
  - `78812db` 후속 수정: 이미 append된 이벤트의 첫 inbox 완료를 `processed`로 집계, 문서 정정
- Evidence artifact: 이 문서만 변경하는 reviewed content의 자식 commit이다. merge 전에 `git rev-parse`로 확인한다.
- Blueprint/plan
  - `docs/03-analysis/message-publication-resilience-observability.blueprint.md`
  - `docs/03-analysis/message-publication-failure-matrix.md`
  - `docs/03-analysis/runtime-persistence-remediation.blueprint.md`
  - `docs/superpowers/plans/2026-10-14-runtime-persistence-guild.md`
  - `docs/superpowers/plans/2026-10-14-runtime-persistence-moderation.md`

## 독립 리뷰

모든 리뷰는 Orca orchestration으로 띄운 fresh Claude 리뷰어가 읽기 전용으로 수행했다(run `run_8608ac37c5b7`).

| 차수 | 구분 | Preset | 대상 SHA | 점수 | P0/P1/P2 | 판정 |
| --- | --- | --- | --- | ---: | --- | --- |
| 1 | Spec compliance | Spec Compliance | `be1c9a9` | 90/100 | 0/0/2 | 통과(P2 수정) |
| 1 | Quality/Security | Code Quality/Security | `be1c9a9` | 78/100 | 0/1/3 | **불합격** → `0e793b7` |
| 2 | Spec compliance | Spec Compliance | `0e793b7` | 92/100 | 0/0/1 | 통과 |
| 2 | Quality/Security | Code Quality/Security | `0e793b7` | 90/100 | 0/0/1 | 통과 |
| 3 | Spec compliance | Spec Compliance | `78812dbcd5a2ebea6da2fdadbf84ee24dbdbe83c` | 91/100 | 0/0/1 | **승인** |
| 3 | Quality/Security | Code Quality/Security | `78812dbcd5a2ebea6da2fdadbf84ee24dbdbe83c` | 90/100 | 0/0/2 | **승인** |

- 2차 통과 후 coordinator가 opt-in drill을 돌렸고, `0e793b7`의 consumer 집계 변경이 `apiPublicationSurvivesListenerRestartAndDuplicateDelivery`를 깨뜨렸다. 그래서 `78812db`로 수정하고 3차 리뷰를 새로 받았다.
- 선행: Codex 코드 리뷰 `docs/04-report/2026-10-22-kafka-runtime-code-review.html`(P2 3건). 이에 대한 Orca Claude 리뷰 2회(94/92, 95/93)는 그 수정분만 대상으로 했다.
- 리뷰 원문은 로컬 artifact로 남아 있다: `C:\tmp\review-extract\premerge-*.md`, `premerge3-*.md`, `premerge4-*.md`.

## Verification (`78812dbcd5a2ebea6da2fdadbf84ee24dbdbe83c`)

| 명령 | 결과 |
| --- | --- |
| `.\gradlew.bat --version` | Gradle 8.14.3 |
| `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar` | 성공 |
| `npm run lint:backend` | 성공 |
| `qa/migration-guard.contract.ps1` | `MIGRATION_GUARD_PASS` |
| `DISCORD_RUN_POSTGRES_TESTS=true` boot:test `--rerun`(독립 `postgres:16-alpine`, DB `discord_runtime_guild_premerge4_20261022`, 종료 후 컨테이너 제거) | PostgresGuildService 6, PostgresGuildStoreContract 5, PostgresModerationServiceContract 8, GuildRuntimeConfiguration 4, ModerationRuntimeConfiguration 3. 실패/skip 0 |
| `DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS=true` boot:test `--rerun`(로컬 broker `127.0.0.1:29092`) | Consumer 6, KafkaConfiguration 19, RecoveryFailure 4, OutboxLease 5, FailureIntegration 11, SchedulingConfiguration 1. 합계 46, 실패/skip 0 |
| drill 스레드 증거 | `DefaultMessagePublicationRelay`/`JdbcMessagePublicationOutbox`는 `scheduling-1`, `MessagePublicationConsumerLag`는 `message-publication-lag-1`에서 실행 |
| `git diff --check 661a6a2 78812db` | 성공 |
| `git merge-base --is-ancestor origin/main 78812db` | 성공 |

- Gradle은 PowerShell `.\gradlew.bat`로 실행했다. Git Bash에서는 AF_UNIX loopback 오류가 난다.
- `lint:backend`는 프로세스 환경에서 `NoDefaultCurrentDirectoryInExePath`를 해제한 뒤 실행했다.

## Blueprint Alignment

- Matches blueprint: yes. `Expected Changed Files`는 위 5개 문서의 합집합이다. `build.gradle.kts`의 `spring-boot-kafka`, Gateway 파일, 2026-10-22 리뷰 반영 파일은 각 blueprint에 명시돼 있다.
- Mismatch: `message-publication-resilience-observability.blueprint.md`의 2026-10-22 Pre-merge 절 Quality P2-1 bullet이 아직 "`duplicate`로 센다"라고 적고 있다. 바로 뒤 문장과 코드(`processed`로 집계)는 서로 맞는다(3차 spec P2-1, 문서 전용). 코드 동작이 기준이며, 후속 문서 정정 대상이다.
- Correct owner of fix: 문서 정정은 후속 task에서 한다.

## Acceptance

- Accepted blueprint: yes
- Accepted implementation: yes. 두 리뷰 모두 90 이상, P0/P1 0
- Accepted verification: reviewed content 기준 yes
- Publishable knowledge:
  - Spring scheduler 함정(`TaskScheduler` bean이 하나라도 있으면 Boot 기본 scheduler가 backoff)
  - ArchitectureTest `..boot..` 패턴이 `org.springframework.boot`에도 걸리는 점
  - Gateway 연산 단위 guild snapshot과 `tracksSubscriptions`
  - 재전달 시 `hasAppended` 선확인

## Residual Risk / Defer

- P2(이관): Gateway 연산 1회의 guild 로드 수가 연산에 등장하는 **서로 다른 guild 수**에 비례한다. 이벤트·세션 수의 곱으로 늘던 문제는 해소됐다.
- P2(이관): 메시지마다 `JdbcModerationService.evaluateMessage`가 guild 행 `FOR UPDATE`를 잡고, 이 lock이 `JdbcGuildStore.get`의 `FOR SHARE`와 경합한다. 수정 방향: lock 없이 평가하고 차단이 확정될 때만 lock을 잡는다. 읽기는 `REPEATABLE READ READ ONLY`로 바꾼다.
- P3 요약
  - drill이 공유 `MeterRegistry`의 절대값을 단언한다(delta로 바꿔야 함). `end_to_end_ms` marker가 누적값이다.
  - 동시 중복 전달 시 `processed`가 2회 집계된다(base와 같음). conflict 분기에 단위 테스트가 없다.
  - 재시도 없이 즉시 DLQ로 가는 경로에서도 `consumer.retries`가 +1 된다.
  - JSON `null` payload가 NPE로 3회 재시도된 뒤 DLQ로 간다.
  - `JdbcGatewayEventLog.contains`가 payload까지 읽는다.
  - evidence JSON과 portfolio 수치가 오래됐다. 공유 recoverer가 authz listener의 소진 record를 message DLQ로 보내는 점이 문서에 없다.
  - moderation의 500건 보존 정책 때문에 OPEN 신고가 밀려날 수 있다.
  - `InMemoryGuildService` test double의 동시성, FQN·import 스타일.
- profile 미지정 로컬 실행은 지원하지 않는다(`GuildService`/`ModerationService` bean이 없음).
- 실제 broker process 중단, 대형 guild의 JDBC 지연·SLO, GitHub Actions CI는 검증하지 않았다.
