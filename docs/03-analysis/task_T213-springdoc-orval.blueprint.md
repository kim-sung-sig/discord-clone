# T213 springdoc + orval API 클라이언트 전환 Blueprint

## Approval Gate
- 상태: 승인됨. 사용자가 2026-10-23 "orval 작업 진행하자"로 승인했다. 계획은 `docs/01-plan/features/T213-springdoc-orval-api-client.plan.md`의 1·2단계를 따른다.
- 이 task 범위: (1) code-first OpenAPI 생성·drift 검사, (2) orval client 생성과 custom mutator, (3) auth 도메인 전환.
- 범위 밖: 나머지 도메인 전환(후속 T215), 오류 응답 형식 통일(후속 T214).
- 막힌 모호성: 없음. 아래 결정으로 해소했다.

## Spike로 확인한 사실 (2026-10-23)
- springdoc-openapi 3.1.1이 최신 release다. 부모 pom이 Spring Boot 4.1.0이라 우리 4.1.1과 맞다. `springdoc-openapi-starter-webmvc-api`(UI 없음)를 쓴다.
- test profile(`test,legacy-auth`)에서 `/v3/api-docs`를 생성하면 88 path, 102 operation이 나온다. 기존 수기 명세는 24 path, 29 operation이었다.
- 수기 명세에만 있던 path는 다음과 같다.
  - `/api/guilds/{guildId}/events/**`, `/api/notifications/**`: controller가 `@Profile("postgres")`라 test context에 없다. → export 전용 profile로 포함시킨다.
  - `/api/security/csp-report`: backend가 아니라 Nuxt server route(`apps/web/server/routes/api/security/csp-report.post.ts`)다. → backend 명세에서 제외하는 것이 맞다.
- 수기 명세의 `ApiErrorResponse{requestId,code,message,status}`를 만드는 코드는 backend 어디에도 없다. 실제 오류는 controller별 `ErrorResponse{message}` 또는 Spring 기본 `/error` JSON이다. → 생성 명세는 실제 동작만 기술하고, 통일은 T214로 분리한다.
- `operationId`는 기본값(method 이름)으로도 1건만 `_1` 접미사가 붙는다. tag는 `auth-controller` 형태다.
- 의존성 하나를 추가하자 Windows에서 test JVM 기동이 `CreateProcess error=206`(명령줄 너무 김)으로 실패했다. 원인은 `backend/boot/build.gradle.kts`가 test runtime classpath 전체를 system property `message.pipeline.test.classpath`로 넘기던 것이다(PR #13). → 파일 경로만 넘기도록 바꾼다(이미 반영). Linux CI에서는 드러나지 않던 결함이다.

## 결정
1. **명세 원본은 코드다.** `docs/api/openapi.json`은 backend test가 springdoc 결과를 정규화해 쓴 산출물이다. `qa/openapi-contract.mjs`(수기 생성기)와 그 산출물 `packages/api-client/src/generated/openapi-types.ts`는 삭제한다.
2. **export context**: profile `test,legacy-auth,openapi-export`. `openapi-export` profile에서는 test 소스의 설정이 postgres 전용 controller와 advice를 `@Bean`으로 등록하고, 저장소 의존성은 Mockito mock으로 채운다. 대상은 `ServerEventController`, `NotificationController`. 앞으로 빠지는 controller가 생기지 않도록, guard test가 main의 모든 `@RestController`가 명세 tag에 나오는지 확인한다.
3. **명세 규칙** (`com.example.discord.openapi.OpenApiConfiguration`, controller에는 어노테이션을 추가하지 않는다)
   - info: title `Discord Clone API`, version `v1`. server는 상대 경로 `/`.
   - securityScheme `bearerAuth`(HTTP bearer). `X-Request-Id` 헤더는 mutator가 자동으로 붙이므로 명세에 넣지 않는다.
   - tag는 controller 이름에서 `Controller`를 뺀 kebab-case다(예: `auth`, `message-outbox`).
   - `operationId`는 `{domain}{Method}`다(예: `AuthController#login` → `authLogin`). method 이름만 쓰면 `list`, `create`처럼 생성 함수 이름으로 쓰기에 너무 일반적이고 controller 사이에 충돌이 나서 `events_1` 같은 접미사가 붙었다. 접미사는 금지하고 test로 고정한다. controller method 이름은 바꾸지 않는다.
   - `Authorization`과 `User-Agent` header parameter는 명세에서 제거한다. `Authorization`을 받던 operation에는 `bearerAuth` security requirement를 붙인다. 내부 운영 header(`X-Internal-*`)는 그대로 둔다.
   - 응답 media type `*/*`는 `application/json`으로 정규화한다. test resources의 `application.yml`이 main 것을 classpath에서 가리므로, yml 설정이 아니라 `OpenApiCustomizer`로 처리한다.
   - **schema 이름 충돌 방지**: controller에 nested로 선언된 record는 domain prefix를 붙인다(`AuthController.SessionResponse` → `AuthSessionResponse`). 이름이 이미 prefix로 시작하면 그대로 둔다. 그래도 서로 다른 class가 같은 이름이 되면 export를 실패시킨다(`DomainQualifiedTypeNameResolver`). 기본 설정에서는 `SessionResponse`(Auth/Gateway), `ErrorResponse`, `MemberRoleResponse`, `UnreadCountResponse`가 같은 이름으로 합쳐졌고, auth 세션 목록 타입이 Gateway session 모양으로 **잘못 생성됐다**.
   - required/nullable: springdoc은 record field의 nullability를 알 수 없다. 그래서 bean validation이 없는 field는 optional로 생성되고, 사실과 다른 required는 선언하지 않는다. 표기 규칙(`@Schema`/nullability annotation)은 오류 형식 통일과 함께 T214에서 정한다. 그때까지 생성 타입을 쓰는 화면은 응답을 검증한 뒤 도메인 타입으로 옮긴다.
4. **운영 노출 차단**: main `application.yml`은 `springdoc.api-docs.enabled: false`다. test resources에서만 `true`로 켠다. Swagger UI 의존성은 넣지 않는다. `OpenApiConfiguration`은 springdoc의 `SpringDocConfiguration`과 같은 조건(`@ConditionalOnWebApplication(SERVLET)`, `@ConditionalOnProperty(springdoc.api-docs.enabled, matchIfMissing=true)`)으로만 등록된다. 비활성이거나 non-web(admin-cli)이면 springdoc의 `ObjectMapperProvider`가 없기 때문이다.
5. **drift 검사**
   - backend: `OpenApiContractTest`가 생성 명세와 커밋된 `docs/api/openapi.json`을 비교한다(정규화 JSON, LF). `-Popenapi.write=true`이면 파일을 갱신한다. CI backend job의 `./gradlew test`가 자동으로 검사한다.
   - frontend: `npm run openapi:check`(`qa/openapi-contract.test.mjs`)가 orval을 `build/openapi-check/`(git ignore, repo 안이라 `node_modules`를 해석할 수 있음)에 생성한 뒤, 커밋된 `packages/api-client/src/generated/`와 파일 목록·내용(LF 정규화)을 비교한다. 핵심 path가 있는지, `operationId` 규칙을 지키는지도 확인한다. working tree는 바꾸지 않는다. CI frontend job과 agent harness 명령은 그대로 유지된다.
   - `npm run openapi:write`: backend export(Gradle) → orval 생성.
6. **orval 8.44.0**(root devDependency)
   - 설정: `orval.config.mjs`, `client: 'fetch'`, `mode: 'tags'`, model 파일 분리, formatter 미지정(결정적 출력), `urlEncodeParameters: true`(기존 `segment()`처럼 path parameter를 encode), `input.filters`로 내부 운영 tag `message-outbox` 제외.
   - custom mutator `packages/api-client/src/http/discord-fetch.ts`
     - 기존 `discord-api.ts` request 동작을 그대로 옮긴다: `credentials: 'include'`, `X-Request-Id` 자동 생성, `bearerToken` → `Authorization`, JSON body일 때 `Content-Type`, 204 → `undefined`, `!ok` → `DiscordRestError(message, status, body)`.
     - `baseUrl`과 `fetcher`는 호출 옵션으로 받는다.
   - `DiscordRestError`는 `packages/api-client`로 옮기고 `apps/web/services/discord-api.ts`가 re-export한다. 그래서 기존 경로와 생성 경로가 같은 오류 class를 던진다.
7. **auth 도메인 전환**: `apps/web/stores/auth.ts`의 refresh, login, sessions, revokeSession, logout을 생성 함수(`@discord-clone/api-client/endpoints/auth`)로 바꾼다. 응답은 `requireSession`/`requireSessions`로 검증한 뒤 store의 도메인 타입으로 옮긴다. 기존 구현은 user가 없는 login 응답도 token을 저장했는데, 이 latent bug를 test로 고정해 막았다. 다른 store는 이번 범위가 아니다. `discordApiPaths.auth`는 shell store가 `auth.guilds()`를 쓰므로 유지한다.
8. 기존 `packages/api-client/src/client.ts`(`createApiClient`, `ApiPath`)는 사용처가 없다(`apps/web` 미사용). mutator로 대체하고 삭제한다.

## Expected Changed Files
- Backend
  - `backend/boot/build.gradle.kts`(springdoc, classpath 파일 전달, `openapi.write` 전달)
  - `backend/boot/src/main/java/com/example/discord/openapi/OpenApiConfiguration.java`, `DomainQualifiedTypeNameResolver.java`(신규)
  - `backend/boot/src/main/resources/application.yml`(`springdoc.api-docs.enabled: false`)
  - `backend/boot/src/test/resources/application.yml` 또는 `application.properties`(test에서 켬)
  - `backend/boot/src/test/java/com/example/discord/openapi/OpenApiContractTest.java`, `DomainQualifiedTypeNameResolverTest.java`, `OpenApiDisabledContextTest.java`, `OpenApiConfigurationConditionsTest.java`(신규)
  - `backend/boot/src/test/java/com/example/discord/event/ServerEventOpenApiExportConfiguration.java`, `.../notification/NotificationOpenApiExportConfiguration.java`(신규, test 전용)
  - `backend/boot/src/test/java/com/example/discord/message/MessagePublicationFailureIntegrationTest.java`(classpath 파일 읽기)
- 명세·생성물
  - `docs/api/openapi.json`(재생성)
  - `packages/api-client/src/generated/**`(orval 생성물, 기존 `openapi-types.ts` 삭제)
- Frontend·도구
  - `.gitattributes`(신규: orval 생성물은 끝 공백 줄 등을 그대로 두므로 `-whitespace`, `linguist-generated`), `orval.config.mjs`(신규), `package.json`·`package-lock.json`(orval, scripts)
  - `qa/openapi-contract.mjs`(삭제), `qa/openapi-contract.test.mjs`(orval drift 검사로 교체)
  - `packages/api-client/src/http/discord-fetch.ts`(신규), `packages/api-client/src/http/discord-fetch.test.ts`(신규), `packages/api-client/src/index.ts`, `packages/api-client/package.json`
  - `packages/api-client/src/client.ts`, `client.test.ts`(삭제)
  - `apps/web/package.json`(`@discord-clone/api-client` workspace 의존성), `apps/web/services/discord-api.ts`(오류 class re-export), `apps/web/stores/auth.ts`, `apps/web/tests/components/auth-api-client.test.ts`(신규), `apps/web/tests/components/security-dashboard.test.ts`(auth refresh 격리)
- 문서
  - 이 blueprint
  - `docs/01-plan/features/T213-springdoc-orval-api-client.plan.md`(path builder 수 정정, 진행 상태)
  - merge 전 evidence `docs/03-analysis/task_T213-merge-review.md`

## 수기 명세와 생성 명세의 차이 (검토 결과)
- 수기 명세는 24 path, 29 operation이었다. 생성 명세는 95 path, 110 operation, 117 schema다.
- 수기 명세가 실제와 달랐던 점
  - 존재하지 않는 `ApiErrorResponse`
  - `CreateMessageRequest`: 실제 이름은 `MessageContentRequest`이고 required 선언이 없다.
  - `ResolveMessageReportRequest.status` enum: 실제로는 `OPEN`도 받는다.
  - backend가 아닌 Nuxt route인 `/api/security/csp-report`
- 생성 명세는 코드가 받는 그대로를 기술한다. 서버 쪽 검증(예: resolve에서 `OPEN` 거부)은 runtime 동작으로 남는다.

## 알려진 한계 (T214/T215로 이관)
- 응답 status: springdoc은 `ResponseEntity`의 runtime status를 알 수 없어서 110개 operation이 모두 `200`만 선언한다. 실제로는 201/204가 있다. 오류 응답(4xx)도 선언되지 않는다. mutator는 status와 무관하게 2xx·204를 처리하므로 runtime 영향은 없다. T214에서 `@ResponseStatus`/`@ApiResponse`와 오류 envelope로 바로잡는다.
- bearer 선언: `@RequestHeader(AUTHORIZATION, required=false)`를 받는 102개 operation에 `bearerAuth`를 붙였다. 대부분은 resolver가 401로 강제하므로 사실상 필수다. 다만 `POST /api/auth/logout`처럼 token 없이도 cookie로 동작하는 경우까지 필수로 보인다. T214에서 선택 인증 표기(`security: [{}, {bearerAuth: []}]`)를 정한다.
- query 직렬화: orval URL 함수는 `null`을 문자열 `'null'`로 보낸다. 기존 `withQuery`는 생략했다. auth 범위에는 query가 없다. T215 전환 체크리스트 항목이다.
- 계약 범위는 `backend/boot` 배포물이다. `backend/services/identity`의 `@RestController`(MFA, Profile, WebAuthn)는 web이 직접 호출하지 않아 제외한다.
- 내부 운영 endpoint: `message-outbox` tag는 web client에서 제외했다. `POST /api/gateway/events`(`X-Internal-Gateway-Publisher`)는 gateway tag에 남아 있다. 명세는 운영에서 제공되지 않고 서버가 header를 검증하므로 보안 경계 변화는 없다.

## Invariants
- 운영 profile(`production`)에서 `/v3/api-docs`는 노출되지 않는다.
- 생성 명세는 결정적이다. 같은 코드면 같은 파일이 나온다(key 정렬, LF, 끝 개행).
- 명세의 모든 operation은 고유한 `operationId`를 갖고 `_N` 접미사가 없다. main의 모든 `@RestController`는 하나 이상의 operation으로 명세에 나온다.
- auth 화면 동작은 바뀌지 않는다: 같은 URL, method, body, `credentials: 'include'`, Bearer header, `X-Request-Id`, 같은 오류 class와 status 분기.
- backend 운영 코드의 동작은 바뀌지 않는다. 예외는 springdoc 의존성, 조건부 springdoc 설정 bean, 비활성 설정 1줄이다. controller는 바꾸지 않는다.
- 배포 설정(api-docs 비활성)과 non-web 실행에서도 application이 기동한다.

## TDD와 Verification Gates
1. RED
   - `OpenApiContractTest`: drift, title, bearer scheme, 상대 server, tag 접미사 없음, operationId 고유·접미사 없음, transport header 제거, 모든 `@RestController` 포함, 같은 이름 record의 schema 분리, main 설정에서 api-docs 비활성.
   - `OpenApiDisabledContextTest`(`springdoc.api-docs.enabled=false` 전체 context 기동, `/v3/api-docs` 404), `OpenApiConfigurationConditionsTest`(non-web·비활성에서 설정 미등록).
   - `discord-fetch.test.ts`: header, bearer, 204, 오류 class, baseUrl.
   - auth store test: 생성 함수 경로로 요청이 나간다.
2. GREEN 후 회귀
   - `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar`
   - `npm run openapi:check`, `npm test`(workspaces), `npm run lint`, web build
   - `git diff --check`
   - Windows에서 PG 계약과 Kafka drill(classpath 파일 전달 회귀 확인)
3. 리뷰: Orca orchestration fresh Claude 리뷰어로 spec compliance와 quality/security를 각각 90 이상, P0/P1 0까지 반복한다.

## 구현 결과 (1차)
- RED→GREEN
  - `OpenApiContractTest`: 수기 명세 대비 drift, transport header, coverage(초기 scanner가 profile 조건으로 16개만 셌던 문제 수정), schema 충돌 각각 RED를 확인한 뒤 GREEN.
  - `DomainQualifiedTypeNameResolverTest`, `discord-fetch.test.ts`: module이 없는 상태에서 RED.
  - `auth-api-client.test.ts`: 기존 store 대비 5건 중 incomplete login 1건 RED(latent bug), 나머지 4건은 동작 고정용 characterization test.
- `openapi:check`는 생성물 한 파일을 변조하면 실패하고 되돌리면 통과한다.
- orval 도입 전후 `npm audit`는 moderate 2건으로 같았다(신규 취약점 없음).
- `security-dashboard.test.ts` 격리
  - 원인: 전체 workspace test에서 5건이 실패했다. Nuxt test app이 부팅될 때 global auth middleware가 `POST /api/auth/refresh`를 보내는데, 이 요청이 그 시점에 stub된 test의 `fetch`로 들어가 `calls[0]`과 응답 순서를 가로챘다. 이 test는 `main`에서도 단독(`-t`)으로 돌리면 같은 이유로 실패하는 기존 순서 의존 test였다. auth 전환으로 refresh 시점이 바뀌면서 전체 실행에서도 드러났다.
  - 수정: test 안의 `stubDashboardFetch`가 refresh에 401로 직접 응답하고, 나머지 요청만 각 test handler로 넘긴다. 21개 stub을 기계적으로 치환했다. 전체 실행 21/21, 단독 실행 모두 통과한다. 운영 코드는 바꾸지 않았다.
- Windows 검증(`t213-verify-v1`)
  - Gradle 전체, lint, migration guard, `openapi:check`, web build 통과.
  - PG 계약 25건, Kafka drill 46건 통과. drill에는 classpath 파일을 읽는 child JVM 경로가 포함된다.
  - workspace test는 위 격리 후 web 160건과 나머지 package 전부 통과했다.

## 1차 리뷰 반영 (`de7cf5b` 대상)
- 결과: Spec 60/100, Quality/Security 60/100. 두 리뷰어가 독립적으로 P0 1건을 보고했다(Orca run `run_d25ac7df02e1`).
- **P0**
  - 현상: main `application.yml`의 `enabled: false`에서는 springdoc이 `ObjectMapperProvider`를 만들지 않는다. 그래서 `domainQualifiedModelResolver`의 주입이 실패해 배포 profile, `bootRun`, CI qa-runtime이 기동하지 않았다.
  - 놓친 이유: test resources가 main yml을 가렸고, yml parse guard는 context를 띄우지 않았다.
  - 수정: 설정 class에 springdoc과 같은 조건을 붙였다.
  - RED/GREEN: 조건을 제거한 상태에서 `OpenApiDisabledContextTest`와 `OpenApiConfigurationConditionsTest` 3건이 `ObjectMapperProvider` 부재로 실패하고, 조건을 되살리면 통과한다.
  - 기동 smoke: `bootJar`를 `SPRING_PROFILES_ACTIVE=postgres,legacy-auth`(CI qa-runtime과 같은 profile), 독립 PostgreSQL, CI와 같은 JWT 설정으로 실행했다. `Started DiscordApplication in 6.449 seconds`, `GET /v3/api-docs` → 404를 확인했다. local Redis가 없어 health는 DOWN이었다(CI는 Redis service를 둔다).
- **P2**
  - path parameter 미encode(기존 `segment()` 대비 퇴행, CSPT 소지): `urlEncodeParameters: true`로 바꿨다. `auth-api-client.test.ts`는 `../../users/@me?x=1#y`가 하나의 encode된 segment로 나가는지 확인한다. `openapi:check`는 생성 path template에 encode되지 않은 `${…}`가 없는지 검사하고, config와 생성물을 함께 되돌린 mutant에서 실패한다.
  - status 200 일괄과 선택적 bearer: 위 "알려진 한계"에 기록했다.
- **P3**
  - schema 이름: `super` 결과(`@Schema(name)` 반영)에 prefix를 붙이고, owner를 class 이름으로 비교한다(classloader 재시작 대비).
  - `jsonResponses`의 paths null을 방어한다.
  - Gradle write run에 `outputs.cacheIf { false }`를 둔다.
  - `openapi:check`는 orval CLI 경로를 `package.json`의 `bin`으로 찾고, scratch 정리를 `finally`에 둔다.
  - login body와 `Content-Type`을 단언한다.
  - 내부 `message-outbox` tag는 client에서 제외했다.
  - blueprint 불일치(test 이름, prettier, method 이름 변경 문구)를 정정했다.
