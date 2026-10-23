# T213 springdoc + orval 기반 API 클라이언트 전환 계획 (다음 작업)

- 상태: **진행 중(2026-10-23 착수)**. 1·2단계는 `docs/03-analysis/task_T213-springdoc-orval.blueprint.md`에서 진행한다. 3단계(나머지 도메인 전환)는 T215, 오류 형식·nullability 규칙은 T214로 분리한다.
- 선행 task: T42(OpenAPI artifact·generated client types·CI drift check)

## 배경과 문제
- 현재 OpenAPI 명세 원본은 사람이 직접 쓰는 `qa/openapi-contract.mjs`(약 510줄)이고, 여기서 `docs/api/openapi.json`과 `packages/api-client/src/generated/openapi-types.ts`를 생성한다.
- 명세에는 24개 path, 29개 operation만 있다. 반면 백엔드 boot 컨트롤러의 매핑 어노테이션(`@Get/Post/Put/Patch/DeleteMapping`)은 약 110개라서, 명세가 실제 API의 1/4 정도만 덮는다.
- 화면은 `apps/web/services/discord-api.ts`에 path builder 48개(T212 리뷰 집계)를 직접 쓴다. 그래서 로드맵 위험 항목 "생성된 `ApiPath`에 없는 경로"가 계속 남아 있다.
- 명세를 손으로 쓰는 한, orval 같은 생성기를 붙여도 어긋남의 위치만 옮겨갈 뿐 사라지지 않는다.

## 목표 구조
```
Controller + record DTO (원본)
  → springdoc-openapi가 빌드 중 openapi.json 추출 (code-first)
  → docs/api/openapi.json 커밋 + CI 차이 검사 (openapi:check 대체)
  → orval → packages/api-client 생성 (함수·타입, 필요하면 Vue Query 훅과 MSW mock)
  → 화면은 생성 함수만 사용. 공통 처리는 custom mutator 하나에 모은다
```

## 단계
1. **springdoc 도입·검증**
   - Spring Boot 4.1에 맞는 springdoc-openapi 버전을 먼저 확인한다(미확인).
   - 빌드 중 `/v3/api-docs`를 파일로 내보낸다(Gradle 플러그인 또는 테스트 덤프). CI 차이 검사를 붙인다.
   - 기존 수기 명세와 비교 보고서를 만든다.
   - 운영 profile에서는 swagger-ui와 api-docs를 끄거나 접근을 제한한다.
2. **orval 생성 + custom mutator**
   - Bearer 토큰, `X-Request-Id`, `DiscordRestError` 변환을 mutator 하나로 옮긴다.
   - auth 도메인 하나를 먼저 전환해 패턴을 검증한다.
3. **나머지 도메인 전환**
   - 도메인별로 생성 클라이언트로 옮긴 뒤 `discord-api.ts`의 직접 작성 경로와 `qa/openapi-contract.mjs`를 제거한다.

## 명세 작성 규칙(1단계에서 문서로 고정)
- `operationId` 이름 규칙(orval이 만드는 함수 이름의 기준), 도메인 단위 tag
- 모든 4xx·5xx 응답에 공통 `ApiErrorResponse` schema
- record DTO의 `required`/`nullable`, `format: uuid`, enum 표기 통일
- securityScheme(Bearer), `X-Request-Id` 헤더

## 주의
- WebSocket Gateway(`apps/web/services/gateway-client.ts`)는 OpenAPI 범위 밖이다. 지금처럼 따로 두거나 나중에 AsyncAPI로 다룬다.
- springdoc 어노테이션은 `io.swagger.v3.oas.annotations` 패키지다. 그래도 도입할 때 ArchitectureTest `domainModulesDoNotDependOnBoot`의 `..boot..` 패턴에 걸리지 않는지 확인한다. 이 패턴은 `org.springframework.boot..`에도 걸린 전례가 있다.
- 자동 생성으로 바꾸면 명세가 수기 명세와 달라진다(이름, 필수 여부 등). 처음 한 번은 차이를 검토하고, 화면 호출부도 함께 바꿔야 한다.
- 대안 `openapi-typescript` + `openapi-fetch`(타입과 타입 안전한 fetch만 제공)는 지금 구조와 가장 가깝다. 기본은 orval로 하되 1단계 끝에 다시 판단한다.
