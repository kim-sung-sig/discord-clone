# 길드 PostgreSQL 상태 전환 검증 근거

## 범위와 결과
운영 길드의 JVM 상태/snapshot wrapper를 제거하고 PostgreSQL을 상태 원본으로 전환했다.
GuildService는 상태를 보관하지 않는다. JdbcGuildStore는 요청 guild만 행 잠금으로 읽고 변경 하위 행을 upsert한다.
모든 운영 caller는 GuildService에 의존한다. 테스트 대역 InMemoryGuildService는 기존 테스트 API를 유지한다.
전체 운영 상태 전환 목표는 아직 미완료이며 이 단계만 수용한다.

## RED
- 실제 PostgreSQL 전용 DB: 기존 wrapper에서 stale instance, FK 메시지 소실, 실패 후 메모리 잔류, 동시 변경 소실 4개 재현.
- 독립 review에서 권한 snapshot 혼합 P1 발견. VIEW와 SEND/MANAGE 또는 target/requester role을 별도 상태에서 읽는 회귀 4개 RED.
- 사용자 fixture username 길이 실패는 수정한 뒤 유효한 RED로 다시 실행했다.

## 구현과 불변식
- guild FOR UPDATE → 동일 connection에서 load/mutation/child upsert/version/outbox → commit.
- get은 FOR SHARE로 하위 행 조회 중 writer를 막는다.
- 하위 행을 삭제하지 않으므로 메시지, server event, invite_role_grants, parent 관계를 보존한다.
- SQLException 및 RuntimeException 시 rollback, 실패 mutation 객체는 서비스에 남지 않는다.
- public 복합 권한 판정은 단일 Guild snapshot에서 계산한다.
- 로그에는 guild ID, operation, committed status만 기록한다.
- 테스트의 cleanup 전 current_database()가 discord_runtime_guild_ prefix인지 fail-closed 검사한다.

## 최종 검증
실행 환경: Git Bash 직접 실행, JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/git/discord/.gradle/runtime-sockets.

| Gate | 최종 결과 |
| --- | --- |
| ./gradlew test checkstyleMain checkstyleTest :backend:boot:bootJar | 성공, 1m30s, 138 tasks |
| 기본 regression XML | tests 468 / failures 0 / errors 0 / skipped 65 |
| 실제 PostgreSQL contract | PostgresGuildStoreContractTest 5, 기존 PostgresGuildServiceTest 6, 실패/오류/skip 0 |
| runtime wiring | GuildRuntimeConfigurationTest 2, 실패/오류/skip 0 |
| 권한 snapshot unit | GuildServicePermissionSnapshotTest 5, 실패/오류/skip 0 |
| 운영 JAR | snapshot adapter 클래스와 boot main InMemoryGuildService bytecode 참조 없음 |
| git diff --check | 성공 |

실제 DB는 postgres-source의 별도 discord_runtime_guild_20261014이다. 기존 discord DB 데이터에는 cleanup을 실행하지 않았다.
현재 infra opt-in 표준 명령:
DISCORD_RUN_POSTGRES_TESTS=true POSTGRES_JDBC_URL=jdbc:postgresql://127.0.0.1:15432/discord_runtime_guild_20261014 ./gradlew :backend:boot:test --tests '*PostgresGuildStoreContractTest' --tests '*PostgresGuildServiceTest' --tests '*GuildRuntimeConfigurationTest'
실제 PostgreSQL의 최종 실행은 권한 수정 이후에 수행했고 XML 시간은 2026-10-14 13:42 UTC다.
기본 gate의 65 skip은 인프라 opt-in 등 조건부 테스트이며 모두 실행했다고 주장하지 않는다.

## 독립 검수
- Plan Review: guild_plan_review 89/100, P0/P1 없음, 세부 transaction/FK/cleanup 계약을 보완.
- 초기 implementation review: spec 88, quality/security 86. 권한 snapshot 혼합 P1 때문에 미수용.
- 구현 수정 및 RED/GREEN, 전체 회귀, 최신 PostgreSQL 재실행 뒤 fresh guild_final_review 수용.
- 최종 spec 100/100, quality/security 98.5/100, P0/P1 없음.
- 검수 기준 source SHA256:
  - GuildService.java d91ee852e6ccb3e72ba9a62e1ad53290c53baba783363602f7cbd242945ffd18
  - JdbcGuildStore.java 38a7d80477093c622e59eaad6a40af6d8b34b51ec34d14dd64225af53ec00afd
- review는 source, schema, RED/GREEN 로그 및 최종 XML을 읽기 전용으로 직접 검사했다.
- 단계 수용이며 전체 목표 완료/merge-ready로 확대하지 않는다.

## 잔여 위험과 후속
P2: JdbcGuildStore.loadChannels는 채널마다 overwrite 조회를 수행한다. 큰 guild의 latency/query count 측정 후 단일 guild overwrite 조회로 묶을 수 있다. 현재 정합성·보안 gate를 막지 않는다.
다음 영역: presence 영구 읽음 상태, social, moderation, storage, voice, experience, gateway dedup, auth login failure, webhook.
