# T214 OpenAPI TDD 기록

## RED

실행 명령:

```bash
./gradlew :backend:boot:test --tests com.example.discord.openapi.OpenApiContractTest.responseContractsDescribeStatusesProblemsAndRequiredValues
```

운영 코드 변경 전에 추가한 테스트 1개가 실패했다. 실패 지점은 `OpenApiContractTest.java:75`의 `/api/auth/signup` 성공 응답 `201` 존재 검사였다. 당시 명세가 `200`만 선언하므로 기대한 실패였다. 실행 결과는 `1 test completed, 1 failed`, `BUILD FAILED in 17s`였다.

원본 RED XML을 별도 보존하지 않았다. 동일 Gradle 출력 디렉터리는 이후 실행으로 갱신되었다. 이 문서는 실행 중 관측한 결과를 기록한다.

## GREEN

동일 집중 테스트를 구현 후 실행해 `BUILD SUCCESSFUL in 19s`를 확인했다.

다음 명령으로 전체 OpenAPI 계약 테스트와 명세 갱신을 실행했다.

```bash
./gradlew :backend:boot:test --tests com.example.discord.openapi.OpenApiContractTest -Popenapi.write=true
```

결과는 `BUILD SUCCESSFUL in 17s`였다. 성공 상태 코드, `204` 본문 부재, 공통 오류 스키마, 응답 필드 필수 여부 및 실제 nullable 필드, 요청 생략 의미 보존을 검사한다.

설치된 `orval/package.json`의 `bin` 경로를 해석해 `node <orval-bin> --config orval.config.mjs`로 클라이언트를 재생성한 뒤 다음을 실행했다.

```bash
node qa/openapi-contract.test.mjs
```

결과: `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163`.

초기 Orval 실행에서는 존재하지 않는 `node_modules/orval/dist/bin/orval.js` 경로를 사용해 `MODULE_NOT_FOUND`가 발생했다. 설치된 패키지의 실제 `bin` 경로를 사용한 재실행은 성공했다.

## 후속 누락 검사

`Response` 접미사가 없는 응답 DTO를 별도로 조사해 `CatalogItem`, `Quest`, `DirectMessage`의 필수 필드가 빠진 사실을 발견했다. 계약 검사를 모든 비요청 객체 스키마로 확대한 뒤 `node qa/openapi-contract.test.mjs`를 실행했다. `CatalogItem response presence must be explicit`이라는 기대한 assertion 실패를 확인한 후 기본 필수 규칙을 `*Request` 이외 객체로 넓혔다. 요청 스키마의 생략·기본값 의미는 유지한다.

마지막 전체 export에서는 새로 추가된 `@Hidden ApiErrorController`가 기존 컨트롤러 전수 검사에 포함되어 9개 중 1개가 실패했다. `/error`는 공개 클라이언트 API가 아니므로 전수 스캐너가 명시적인 `@Hidden`을 존중하도록 수정했다. 이후 같은 전체 export 명령으로 **9개 테스트 모두 통과**, `BUILD SUCCESSFUL in 17s`를 확인했다. 최종 로그는 `C:/tmp/t214-openapi-final.log`에 남겼다.

마지막 Orval 재생성 및 JavaScript 계약 검사도 다시 통과했다: `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163`. 성공 상태 코드 분포는 `200` 75개, `201` 25개, `202` 1개, `204` 9개다. `202`는 내부 운영용 replay이며 기존 Orval 설정에 따라 웹 클라이언트 생성 대상에서 제외된다.
