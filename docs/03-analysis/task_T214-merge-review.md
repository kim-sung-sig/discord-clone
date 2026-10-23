# T214 병합 검수 증거

## 고정 대상
- branch: `task_T214-problem-details`
- base origin/main SHA: `76db98d412f6af240e0b093737ed0aaea53baa9a`
- reviewed content SHA: `a1b0270fb708571431faba70634650895d832950`
- spec review 대상 SHA: `a1b0270fb708571431faba70634650895d832950`
- quality/security review 대상 SHA: `a1b0270fb708571431faba70634650895d832950`
- verification 대상 SHA: `a1b0270fb708571431faba70634650895d832950`
- 승인 blueprint: `docs/02-design/T214-problem-details-blueprint.md`, 사용자 2026-10-24 구현 지시로 승인.
- 현재 branch tip은 이 파일만 추가하는 evidence commit으로 외부 계산한다. parent는 위 reviewed content SHA여야 한다. 자기 commit SHA를 본문에 순환 참조하지 않는다.
- 구현 commit: `eccf46b64942d53f436cb30b9acef6f49fe4fd77`, 보정 commit: reviewed content SHA.

## 검수 과정
- 첫 content `eccf46b`는 spec 88/100 및 내부 outbox nullable 누락 P1으로 불합격했다. push/merge하지 않았다.
- 설계 보정 → 두 회귀 테스트 RED → nullable metadata와 안전 진단 로그 수정 → GREEN → 새 content commit 순서를 거쳤다. `docs/03-analysis/T214-tdd-verification.md` 및 `T214-openapi-tdd.md`에 관측과 한계를 기록했다.
- 보정 후 새 context의 `spec_review_v2`와 `quality_review_v2`가 현재 고정 content를 독립 검수했다. 검수 중 제품·설계 파일은 수정하지 않았다.

## Spec Compliance Review
- Preset: Implementation Review, 최소90/100, P0/P1 없음.
- 독립 reviewer: `spec_review_v2`.

| 항목 | 점수 | 근거 |
| --- | ---: | --- |
| Blueprint alignment | 19/20 | 공통 오류·429·fallback·프론트 동시 변경·Node·T215 제외 |
| Correctness | 19/20 | 기존 상태와 auth/recovery code 유지, 실제 nullable 보정 |
| Security/privacy | 15/15 | 원문 비공개, 요청 ID 검증·query 제외 |
| Tests/verification | 18/20 | 오류·명세·내부 variant·RED/GREEN 검토, 인프라 한계 |
| Maintainability | 14/15 | 중복 advice 제거, factory 공유, nullable 수동 목록 |
| Operations/docs | 9/10 | 429 헤더·추적 유지, 아래 P2 남음 |
| **합계** | **94/100** | **통과, P0 0 / P1 0 / P2 1** |

독립 정적 비교에서 요청 스키마 41개가 기준 SHA와 동일했다. 성공 응답은 200×75, 201×25, 202×1, 204×9였으며 operation마다 성공 상태가 하나다. 내부 outbox author3개·target5개 identifier의 required+nullable와 회귀 테스트를 확인했다.

## Code Quality Review
- Preset: Implementation Review, 최소90/100, P0/P1 없음.
- 독립 reviewer: `quality_review_v2`.

| 항목 | 점수 | 근거 |
| --- | ---: | --- |
| Blueprint alignment | 20/20 | 오류·생성 계약·프론트·Node 범위 |
| Correctness | 19/20 | advice별 이전, Stage400/Thread403 보존 |
| Security/privacy | 15/15 | 권한 경계 보존·예상 밖500 원문 비공개 |
| Tests/verification | 18/20 | 오류·헤더·누출·계약 회귀, 외부 인프라 제외 |
| Maintainability | 14/15 | 17개 중복 advice 제거·공통 재사용 |
| Operations/docs | 8/10 | MVC 안전 로그 보강, fallback 진단 P2 |
| **합계** | **94/100** | **통과, P0 0 / P1 0 / P2 1** |

## Security Review
- Preset: blueprint에 선언된 Security Review, 최소90/100.
- reviewer: `quality_review_v2`. 최초 별도 항목 구성의 97점은 폐기하고 같은 SHA를 선언 항목으로 재평가한 아래 **96점**만 사용한다.

| 항목 | 점수 | 근거 |
| --- | ---: | --- |
| Threat coverage | 19/20 | MVC/framework/429/fallback/client 경계, 실서버 장애 주입 제외 |
| Access control | 20/20 | 인증·인가·401/403·세션 경쟁 방지 보존 |
| Data exposure | 15/15 | 원문 비공개·안전한 로그 필드만 기록 |
| Regression tests | 19/20 | 비밀 문자열·요청 ID·query·복구 코드·헤더 회귀 |
| Operational safety | 13/15 | 제한·추적 보존, fallback 진단 P2 |
| Dependency/config safety | 10/10 | 의존성 추가 없음, Node engine만 package/lock 동기화 |
| **합계** | **96/100** | **통과, P0/P1 없음** |

## 동일 SHA 검증
`C:/tmp/t214-exact-verify.sh`는 위 reviewed content SHA를 출력하고 clean diff를 확인한 뒤 아래를 순차 실행했다. 마지막 exit code는 0이며 실행 후 working tree도 clean이었다.

| 명령 | 결과 |
| --- | --- |
| `./gradlew test checkstyleMain checkstyleTest` | `BUILD SUCCESSFUL in 1m 34s`, 136 tasks(3 실행,133 up-to-date) |
| `node qa/openapi-contract.test.mjs` | `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163` |
| `CI=true npm test --workspaces` | 190 통과, 기존 web 8 skip |
| `npm run lint:frontend` | exit0 |
| `PATH="$PWD:$PATH" npm run lint:backend` | exit0, wrapper Checkstyle 성공 |
| `npm run build -w apps/web` | exit0, 11.1s |
| `git diff --check` / `git status --short` | exit0 / 출력 없음 |

보존 로그: `qa/artifacts/T214/a1b0270-{gradle,openapi,workspaces,frontend-lint,backend-lint,build}.log`. 이 디렉터리는 기존 정책에 따라 gitignored이며, 검수 시 로컬에서 확인한다. TDD 보정 RED/GREEN은 같은 디렉터리의 `review-fix-{red,green}.log`에 보존했다. 원래 일부 RED XML이 후속 실행으로 덮어써졌다는 한계는 TDD 문서에 명시했다.

## 잔여 위험과 명시적 Defer
- **P2 1건 수용**: `backend/boot/src/main/java/com/example/discord/api/ApiErrorController.java:20`은 `ERROR_EXCEPTION`을 동반한 servlet/container fallback500의 자체 원인 로그를 남기지 않는다. MVC500은 exception type·origin·requestId를 안전하게 기록한다. 응답 형식·상태·원문 비공개는 양 경로 모두 충족한다. fallback에도 같은 진단을 확대하는 작업은 운영 후속으로 남긴다. 원문 message/raw stack trace를 무조건 기록해서는 안 된다.
- 외부 PostgreSQL/Redis/Kafka opt-in 통합 테스트, 실제 servlet container error dispatch 장애 주입, 배포는 수행하지 않았다. 기존 skip 정책을 변경하지 않았다.
- 기존 Mockito agent/CDS 경고와 Nuxt 의존성 Rollup 주석 경고가 있다.
- Windows Node Gradle helper가 기본 PATH에서 wrapper를 못 찾는 환경 문제는 명령 단위 PATH 보정으로 검증했다. helper 자체는 범위 밖으로 유지했다.
- T215 잔여 호출 전환은 하지 않았다. 오래된 브랜치는 요청의 9개/원격5개와 현재 목록이 달라 삭제하지 않았다.

## Blueprint Alignment와 Required Loop Action
- 주요 blueprint 요구: 일치. 공통 RFC9457+code/requestId, 정확한 상태·오류 명세, required/nullable, 프론트 동시 변경, Node 최소 버전 반영.
- 명시적 차이: fallback500 자체 진단 로그는 위 P2로 수용한다. 사용자 T214의 오류 응답·생성 타입 계약을 막는 결함은 아니다.
- 내부 outbox P1: 수정·회귀 테스트·명세 재생성·fresh review로 해소.
- 추가 제품 수정: 현재 필요 없음. 독립 evidence 검수가 gap을 찾으면 이 branch에서 수정하고 새 review/verification loop를 수행한다.

## Acceptance와 최종 게이트
- blueprint: 승인.
- spec94 / quality94 / security96, P0/P1 없음.
- 동일 reviewed SHA 전체 verification: 성공.
- 구현·검증 acceptance: 충족. **이 evidence commit 자체의 독립 검수가 끝나기 전에는 merge-ready로 간주하지 않는다.**
- evidence audit는 이 파일의 점수뿐 아니라 실제 SHA·parent·파일 범위·로그·명세를 독립 확인해야 한다.
- merge 전 fetch 후 base, ancestor, clean index, content diff와 blueprint 범위를 다시 대조한다.
- Wiki는 독립 evidence audit도 통과한 뒤 Backend Architecture, Frontend Client Architecture, QA Infra Operations 및 log에 승인된 계약만 게시한다. 새 페이지 추가는 없으므로 index는 유지한다.
