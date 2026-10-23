# T214 TDD·검증 기록

## 대상과 승인
- 기준 `main`: `76db98d`.
- 작업 브랜치: `task_T214-problem-details`.
- 승인 설계: `docs/02-design/T214-problem-details-blueprint.md`.
- 사용자 지시: 2026-10-24 `$test-driven-development 구현시작`.
- 이 문서는 에이전트 검수용 실행 증거다. 독립 검수·merge acceptance는 별도 `task_T214-merge-review.md`에서 고정 SHA로 기록한다.

## RED → GREEN
| 범위 | 먼저 확인한 실패 | 최소 구현 후 확인 |
| --- | --- | --- |
| 공통 서버 오류 | `ApiExceptionHandlerTest` 7개 실패. 기존 본문/미처리 예외에는 ProblemDetail·code·requestId가 없었다. | 전체 backend test에서 새 오류 테스트 통과 |
| 429 필터 | `OperationalHardeningFilterTest`의 새 detail 계약 검사가 기존 `{message}`에서 실패. 로그 `C:/tmp/t214-backend-pair.log`는 후속 실행으로 갱신됨. | 공통 factory+주입된 mapper 직렬화 후 rate limit·헤더·프록시 회귀 통과 |
| servlet fallback | `ApiExceptionHandlerTest.servletErrorDispatchUsesSameSafeProblemContract`가 기존 `/error`의 JSON content type에서 실패. `C:/tmp/t214-fallback-red.log`. | `ApiErrorController` 추가 후 전체 backend test에서 통과 |
| OpenAPI | 201 누락 및 `CatalogItem` required 누락을 assertion 실패로 확인 | 상세 `T214-openapi-tdd.md`, 최종 export 9/9 및 생성물 drift 검사 통과 |
| 공유 오류 클래스 | 추가한 detail/code/requestId 접근 테스트 1개 실패, 기존 5개 통과. `C:/tmp/t214-fe-api-red.log` | API client 6개 통과 |
| auth/shell | 잠김/권한 거부 코드 구분·shell detail 표시 테스트 3개 실패. `C:/tmp/t214-fe-web-red.log` | 전체 workspace 190개 통과, 기존 web 8개 skip |

TDD 중 raw 로그는 임시 파일이며 일부 공통 Gradle XML은 후속 실행으로 덮어썼다. 보존하지 않은 원본 로그가 있다고 주장하지 않는다.

## 구현 중 발견과 처리
- Spring 7 `ProblemDetail`의 기본 type은 생략될 수 있어 `setType(URI.create("about:blank"))`로 명시했다. 별도 응답 DTO나 serializer는 추가하지 않았다.
- auth 예외가 package-private이므로 공통 처리기를 auth 패키지에 두어 예외 공개 파일 분할을 피했다.
- Stage `IllegalStateException`→400, Thread→403 기존 계약은 실제 handler 유형을 기준으로 중앙에서 보존했다. 다른 내부 상태 예외는 500으로 숨긴다.
- 업로드 검증 실패의 기존 `invalid attachment` 문구는 공통 `invalid request`와 `INVALID_REQUEST`로 통일했다. 상태는 400을 유지한다.
- 최초 전체 backend 회귀에서 새 테스트의 `$.message` 부재 검사가 일괄 마이그레이션으로 `$.detail`로 바뀐 실수를 잡아 복구했다. 같은 실행의 업로드 문구 expectation도 새 공통 계약에 맞췄고 전체 검증을 재실행했다.
- 한 차례 병렬 Gradle 실행이 동일 binary 결과 디렉터리를 잠갔다. 이후 공유 build의 Gradle 실행은 직렬화했다.

## 최종 검증
| 명령 | 결과/증거 |
| --- | --- |
| `./gradlew --version` | Gradle 8.14.3 / Java 21.0.7 |
| `./gradlew test` | `BUILD SUCCESSFUL in 1m 23s`, 92 tasks. `C:/tmp/t214-gradle-test.log`. 외부 인프라 opt-in 테스트 skip 정책 유지 |
| `./gradlew checkstyleMain checkstyleTest` | `BUILD SUCCESSFUL in 19s`. `C:/tmp/t214-checkstyle.log` |
| `PATH="$PWD:$PATH" npm run lint:backend` | `BUILD SUCCESSFUL in 4s`. `C:/tmp/t214-lint-backend-path.log` |
| OpenAPI export test | 9/9 통과. `C:/tmp/t214-openapi-final.log` |
| `node qa/openapi-contract.test.mjs` | `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163` |
| `CI=true npm test --workspaces` | 190 통과: web164, desktop4, api6, backports10, platform2, ui4. 기존 web 8 skip. `C:/tmp/t214-fe-workspaces-green.log` |
| `npm run lint:frontend` | exit0. `C:/tmp/t214-fe-lint-final.log` |
| `npm run build -w apps/web` | exit0, 14.5s. `C:/tmp/t214-fe-build-final.log` |
| `git diff --check` | exit0. `C:/tmp/t214-diff-check.log` |
| Node 버전 | v24.12.0. 루트 package와 lockfile 최소 요구 `>=22.18.0` |

## 독립 검수 보정 루프
- 첫 구현 content SHA `eccf46b64942d53f436cb30b9acef6f49fe4fd77`는 품질 92/100·보안97/100이었으나 명세88/100 및 P1 1건으로 불합격했다. 이 revision은 push/merge 근거로 사용하지 않았다.
- P1: 내부 outbox author/target의 variant별 null identifier 8개가 export된 명세에서 non-null로 선언됐다. 웹 생성 대상 제외와 내부 명세 정확성을 잘못 동일시한 누락이다.
- P2: catch-all 500의 요청 상태 로그는 있었지만 예외 종류/발생 위치 로그가 없었다.
- 설계서를 먼저 보정한 후 `outboxVariantIdentifiersAreRequiredButNullable` 및 500 안전 진단 로그 assertion을 추가했다. 두 테스트가 각각 예상한 assertion으로 실패했다: `2 tests completed, 2 failed`, `BUILD FAILED in 20s`. 보존 로그 `C:/tmp/t214-review-fix-red.log`.
- 기존 nullable map에 author `userId/botId/webhookId`, target `guildId/channelId/threadId/conversationId/recipientId`를 추가했다. required 존재 규칙은 그대로다. 명세를 backend export로 재생성했고 웹 생성물은 변경되지 않음을 drift 검사로 확인했다.
- 5xx에서만 exception class·첫 stack frame·requestId를 기록한다. exception message, cause message, raw stack trace는 기록하지 않는다. 새 테스트가 `database-password` 원문이 응답과 로그 모두에 없음을 검사한다.
- `./gradlew :backend:boot:test --tests com.example.discord.auth.ApiExceptionHandlerTest --tests com.example.discord.openapi.OpenApiContractTest -Popenapi.write=true`는 `BUILD SUCCESSFUL in 19s`, 이어 `node qa/openapi-contract.test.mjs`는 `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163`였다. GREEN 로그 `C:/tmp/t214-review-fix-green.log`.
- 보정 후 새 SHA에서 fresh spec/quality/security 검수 및 전체 verification을 다시 수행한다. 최종 승인 여부와 정확한 SHA는 merge-review artifact에 기록한다.

## 환경 제한과 잔여 범위
- Windows에서 `npm run lint:backend`의 기존 helper가 기본 PATH로 `gradlew.bat`를 찾지 못했다. 저장소 경로를 해당 명령의 PATH에 추가한 실행과 Git Bash 직접 wrapper 실행은 모두 성공했다. helper 자체는 범위 밖이라 변경하지 않았다.
- 기본 실행 도구의 quoted Git Bash shell 경로는 WSL bash로 잘못 연결됐다. `cmd.exe`에서 `C:\Progra~1\Git\bin\bash.exe C:/tmp/<script>.sh`로 실제 Git Bash를 실행했다. 저장소 작업은 모두 해당 Git Bash 안에서 수행했다.
- 기존 Mockito 동적 agent/CDS 경고, Nuxt 의존성의 Rollup `__NO_SIDE_EFFECTS__` 경고가 있다. 이번 변경으로 경고 없는 빌드를 달성했다고 주장하지 않는다.
- PostgreSQL/Redis/Kafka opt-in 통합 테스트와 운영 배포는 실행하지 않았다. 저장소 기본 gate와 오류 경로의 MockMvc 통합 검증을 수행했다.
- T215 호출 전환은 제외했다. 오래된 브랜치 삭제는 사용자가 언급한 9개/원격5개와 실제 현재 목록이 달라 대상 확정 전 실행하지 않았다. 다른 worktree 사용 브랜치도 유지한다.

## Blueprint Alignment
- Matches blueprint: yes, 공통 오류 형식·필수/nullable·정확한 상태·프론트 동시 변경·Node 요구 버전 반영.
- Mismatch: auth 공통 처리기 패키지는 접근 제약을 고려해 설계서를 먼저 보정했다. nullable 목록은 도메인 생성자와 projection 경로로 확정했다.
- Correct owner of fix: 구현 검수에서 새로운 결함이 나오면 같은 task branch에서 수정한다.

## Required Loop Action
- Update blueprint: 새 결함이 설계 변경을 요구할 때만 수행.
- Update implementation: 독립 검수 결과에 따름.
- Re-run synthesis: 현재 불필요.
- Re-run verification: 고정 reviewed content SHA에서 gate를 재확인하고 기록한다.

## Acceptance
- Accepted blueprint: 사용자 승인 완료.
- Accepted implementation / verification: 최종 독립 검수와 SHA 검증 전에는 merge 승인으로 간주하지 않는다.
- Publishable knowledge: 검수 통과 후 API 오류 계약·생성 타입 규칙·검증 명령만 Wiki에 반영한다.
