# T213 Merge Review

## 대상

- Task branch: `task_T213-springdoc-orval`
- Base `origin/main` SHA: `1fb5a6fb9e730d9db417583a23cbda394d4660d5`
- Reviewed content SHA: `e97fa11fe67fd12ea21bdae78dc4bb763abd608a`
  - `de7cf5b` 구현: springdoc code-first 명세, orval client, auth store 전환, Windows classpath 파일 전달
  - `e97fa11` 1차 리뷰 P0/P2/P3 반영: 배포 profile 기동 조건, path parameter encode
- Evidence artifact: 이 문서만 변경하는 reviewed content의 자식 commit이다.
- Blueprint: `docs/03-analysis/task_T213-springdoc-orval.blueprint.md`
- Plan: `docs/01-plan/features/T213-springdoc-orval-api-client.plan.md`

## 독립 리뷰

Orca orchestration으로 띄운 fresh Claude 리뷰어가 읽기 전용으로 검수했다(run `run_d25ac7df02e1`).

| 차수 | 구분 | Preset | 대상 SHA | 점수 | P0/P1/P2 | 판정 |
| --- | --- | --- | --- | ---: | --- | --- |
| 1 | Spec compliance | Implementation Review | `de7cf5b` | 60/100 | 1/0/3 | **불합격** |
| 1 | Quality/Security | Implementation + Security Review | `de7cf5b` | 60/100 | 1/0/3 | **불합격** |
| 2 | Spec compliance | Implementation Review | `e97fa11fe67fd12ea21bdae78dc4bb763abd608a` | 94/100 | 0/0/0 | **승인**(P3 7) |
| 2 | Quality/Security | Implementation + Security Review | `e97fa11fe67fd12ea21bdae78dc4bb763abd608a` | 93/100 | 0/0/0 | **승인**(P3 6) |

- 1차 P0
  - 현상: main `application.yml`의 `springdoc.api-docs.enabled: false`에서는 springdoc의 `ObjectMapperProvider`가 없다. 그래서 `OpenApiConfiguration` 주입이 실패했고, 배포 profile, `bootRun`, CI qa-runtime이 기동하지 않았다.
  - 놓친 이유: test resources의 `application.yml`이 main 것을 classpath에서 가렸다.
  - 처리: `e97fa11`에서 springdoc과 같은 조건, context 회귀 test, 실제 `bootJar` 기동 smoke를 추가했다.
- 리뷰 원문은 로컬 artifact로 남아 있다: `C:\tmp\review-extract\t213-*-review.md`, `t213-*-review2.md`.

## Verification (`e97fa11fe67fd12ea21bdae78dc4bb763abd608a`, Windows)

| 명령 | 결과 |
| --- | --- |
| `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar` | 성공(`OpenApiContractTest` drift 포함) |
| `npm run lint` | 성공 |
| `qa/migration-guard.contract.ps1` | `MIGRATION_GUARD_PASS` |
| `npm run openapi:check` | `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=162` |
| `npm test`(workspaces) | web 161 passed / 8 skipped, 나머지 package 전부 통과 |
| `npm run build --workspace @discord-clone/web` | 성공 |
| `DISCORD_RUN_POSTGRES_TESTS=true`(독립 `postgres:16-alpine`) | PostgresGuildService 6, PostgresGuildStoreContract 7, PostgresModerationServiceContract 12. 실패/skip 0 |
| `DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS=true` Kafka drill | 46건, 실패/skip 0. classpath 파일을 읽는 child JVM 경로 포함 |
| `bootJar` 기동 smoke(`postgres,legacy-auth`, 독립 PostgreSQL, CI와 같은 JWT 설정) | `Started DiscordApplication in 7.592 seconds`, `GET /v3/api-docs` 404. local Redis가 없어 health는 DOWN |
| `git diff --check` | 성공 |
| `npm audit`(orval 전후) | moderate 2건으로 같음(신규 없음) |

## Blueprint Alignment

- Matches blueprint: yes. 2차 spec 리뷰가 생성물을 제외한 diff 파일 32개를 Expected Changed Files와 대조했다.
- Mismatch: 없음. 알려진 한계는 blueprint "알려진 한계" 절에 이관 항목으로 명시돼 있다.

## Acceptance

- Accepted blueprint: yes
- Accepted implementation: yes
- Accepted verification: reviewed content 기준 yes. Node 22에서 orval 출력이 같은지는 CI frontend job이 확인한다.
- Publishable knowledge
  - 명세 원본은 controller이고, 수정 절차는 `npm run openapi:write` → `openapi:check`다.
  - operationId와 schema 이름 규칙을 정했다.
  - test resources의 `application.yml`이 main 설정을 가리는 함정이 있다.
  - 운영 profile에서 springdoc을 끄는 조건이 필요하다.
  - Windows test JVM 명령줄 길이 한계 때문에 classpath를 파일로 넘긴다.

## Residual Risk / Defer

- T214(오류 형식·nullability)
  - 모든 operation이 `200`만 선언한다.
  - 오류 응답이 선언되지 않았다.
  - 선택 인증(logout)도 bearer 필수로 보인다.
  - required/nullable 표기 규칙이 없어, 생성 타입이 대부분 optional이다.
- T215(나머지 도메인 전환)
  - orval query 직렬화가 `null`을 `'null'`로 보낸다.
  - `gatewayPublish`의 `X-Internal-Gateway-Publisher` header를 생성 함수가 표현하지 못한다(web 미사용).
- P3
  - root `engines.node`를 orval 요구치 `>=22.18.0`으로 올려야 한다.
  - 불완전한 세션 응답 오류의 사용자 문구를 분리해야 한다.
  - `OpenApiContractTest`를 `@BeforeAll` 1회 생성으로 바꿔야 한다.
  - test 전체에서 springdoc이 켜져 있다.
  - Gradle input이 절대 경로다(build cache relocate 불가).
  - swagger-core가 요구하는 Jackson 2.22 대신 Boot BOM의 2.21.x로 해석된다. 생성 test는 통과하고 운영에서는 생성이 꺼져 있다. springdoc이나 Boot를 올릴 때 확인해야 한다.
  - admin-cli 기동 smoke는 실행하지 않았다(조건 test와 정적 검증으로 대체).
