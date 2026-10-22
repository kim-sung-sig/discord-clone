# 모더레이션 원본 PostgreSQL 전환 검증

## Task Packet / 기준
- Task ID: runtime-persistence-moderation. Domain: onboarding/AutoMod/audit/alerts/reports.
- baseline cb973c14. 계획 docs/superpowers/plans/2026-10-14-runtime-persistence-moderation.md, SHA256 58bda9fe65f32d28c9d0164f31c4ea4fe3e057d954b7ab2552d3b84b077cf624.
- 독립 Plan Review93/100, P0/P1 없음. P2 DDL/index/order, DB clock, historic target 수명은 구현·검증에 반영.
- Allowed write paths: 계획 Expected Changed Files. Forbidden: 원본 C:/git/discord dirty 변경, unrelated features, HTTP 계약 변경, main 직접 commit/push.
- 기존 controller bearer actor, guild/channel permission, message lookup 및 DTO를 유지. Moderation/Guild/Invite/Message/Stage/Premium caller는 서비스 타입만 교체.
- External dependency: PostgreSQL, postgres profile. test classpath !postgres에서만 fake Bean. 새로운 외부 의존성 없음.

## 구현 경계
- ModerationService + JdbcModerationService. boot main InMemoryModerationService 참조 없음. startup 전체 적재 없음.
- V21은 questions/answers/rules/audit/alerts/reports를 저장. PostgreSQL TEXT[]/UUID[], 순서 ordinal 및 guild/filter index.
- question+answers, rule+audit, AutoMod block+alert+audit, report+audit, resolve+audit가 같은 JDBC transaction.
- guild row FOR UPDATE로 mutation/retention 직렬화. audit/alert/report 최근500을 같은 transaction에서 정리. 조회 default50/max100 유지.
- question/answer 입력 순서, 첫 matching rule, reports 생성순, audit/alert 최신순을 ordinal로 유지.
- clock_timestamp() 및 이전 guild audit timestamp+1micro로 단조 시각을 제공. process-local lastAuditCreatedAt 없음.
- 공유 domain matching/resolve 정책 사용. report는 DB 저장값을 반환해 재조회 시각과 응답이 일치.
- audit target 및 report message/channel에는 삭제 cascade FK를 두지 않아 과거 참조 수명 유지. guild owner root만 FK cascade.
- 로그는 safe IDs/action/operation/effects 및 pending_commit/committed 구분. 본문·keywords·reason·secret을 기록하지 않음.

## RED / 실제 실패 재현
- 운영 memory Bean 존재로 ModerationRuntimeConfigurationTest RED 확인(2 tests 중1 failure, architecture 자동포함).
- actual PG에서 report resolve 응답 나노초와 PostgreSQL microsecond 저장값 차이를 재현. 같은 transaction에서 저장 record를 재조회해 반환하여 정밀도 불일치를 수정.

## Verification
- ./gradlew --version: Gradle8.14.3/Java21, Git Bash via PowerShell carrier, JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/git/discord/.gradle/runtime-sockets.
- focused module8, controller6, runtime2 PASS.
- actual PostgresModerationServiceContractTest8 PASS, failure/error/skip0. task DB discord_runtime_guild_20261014 prefix 확인 후 migrate21.
- 계약: fresh adapter 전체 state, guild isolation, question/answer/role 순서, first/disabled rule, report resolve, filter/page/500 retention, deterministic DB lock wait, strict audit timestamp, question 이전 answer 및 rule/report/resolve/alert+audit rollback과 복구.
- 동시성: 첫 connection이 guild row lock을 보유하고 pg_stat_activity에서 두 worker의 wait_event_type='Lock'을 관찰. 둘 다 미완료임을 확인 후 release. 최종 audit count500 및 latest timestamps strict ordering 확인.
- ./gradlew test checkstyleMain checkstyleTest :backend:boot:bootJar PASS(1m13s). XML 전체502/실패0/오류0/조건부skip88.
- npm run lint:backend PASS(98 tasks), pwsh -NoProfile -File qa/migration-guard.contract.ps1 MIGRATION_GUARD_PASS.
- git diff --check PASS. JAR JdbcModerationService/V21 포함.
- ignored evidence .gradle/moderation-evidence: full-results.txt, actual XML, jar-contents.txt 및 review.diff. 로그 /tmp/moderation-*.log.

## 한계 / Acceptance
- fresh adapter/wiring 수준이며 실제 app process restart·배포는 미검증.
- GuildService 역할 변경/다른 use-case의 state 변경과 controller appendAudit는 기존 별도 transaction 경계를 유지. 그 전체 원자성을 주장하지 않음.
- guild lock이 AutoMod allowed evaluation도 직렬화하는 성능 상한이 있음. 측정상 필요할 때 policy read와 retention write 분리. 코드 ponytail 주석에 기록.
- 초기 독립 spec95/quality-security94, P0/P1 없음. 코드 검토와 실제PG XML 대조 수용.
- P2 전체 XML 보존 공백: ./gradlew test 재실행 PASS(1m11s) 후 모든 XML을 .gradle/moderation-evidence/full-xml/ 아래 원래 경로로 보존. 직접 재집계502/0fail/0error/88skip, full-xml-totals.txt. actual focused XML은 별도 기존 위치에 보존해 서로 덮어쓰지 않음.
- 비차단 P2: allowed evaluation도 guild row lock을 사용해 처리량 상한이 있음. 실제 lock wait/처리량 측정 후 read/write 경계 분리 조건을 코드와 위 문단에 명시.
- 최종 신규 독립 검토: tree ac43fdc3e38f900cf89269a6a5899e478dd35bd5, spec96/quality-security94, P0/P1 없음. 잔여 처리량 P2 하나 비차단으로 수용.
- 검토자가 전체 XML124개를 독립 재집계502/0/0/88 확인. 별도 actual PG8/runtime2/controller6 skip0 및 전체 ArchitectureTest3/0/0/0 확인.
- staged tree/diff 일치, diff check, JAR JDBC class bytes/V21 소스 일치 및 실행 로그를 직접 대조함. 테스트 재실행과 실제processrestart는 reviewer 범위에서 하지 않음.
- 전체 runtime persistence 목표는 미완료, push/merge 없음.

## Blueprint Alignment / Acceptance
- Matches blueprint: yes. 포트/순수 정책/JDBC 원본/보존/잠금 및 기존 HTTP boundary 일치.
- Required Loop Action: blocking 수정 없음. 처리량 측정 후 read/write 분리 P2는 후속 개선 조건.
- Accepted blueprint: yes(plan93). Accepted implementation: yes(final spec96/quality-security94).
- Accepted verification: yes. 전체 원본 XML 보존 공백을 보완한 뒤 독립 검수 수용.
- Publishable knowledge: V21 원본 경계,500 retention/ordinal/DB audit clock, actualPG 검증 및 DB 정밀도 응답 pitfall.
- Wiki used: index.md, Backend Architecture.md, QA Infra Operations.md, Agent Development Guide.md.
