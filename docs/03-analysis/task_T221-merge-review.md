# T221 병합 검토 기록

## 대상

- 작업 브랜치: `task_T221-message-author-names`
- 기준 `origin/main` SHA: `ecf24e3b880e0c5cbd74a588be52c0f47f10a8ca`
- 검토한 내용 SHA: `fa74c01908d60150d36386dbc25ed87a5faef6be`
  - `bfec8c4`: 설계 문서
  - `d42472a`: 메시지 응답과 실시간(Gateway) 이벤트에 작성자 표시 이름(`authorDisplayName`)을 붙인다.
    - message module에 port `MessageAuthorDirectory`를 두고, users table을 가진 boot `auth` package가 구현한다(`AuthStore.findProfilesByIds`).
  - `7d8d81e`: OpenAPI에 `authorDisplayName`을 필수·nullable로 선언한다.
  - `052a9f9`: 웹 화면에서 작성자 이름과 아바타 글자를 표시 이름으로 보여 준다. 식별 번호(UUID)는 어떤 경우에도 보이지 않는다.
  - `fa74c01`: 코디네이터가 `npm run openapi:write`로 다시 생성한 `docs/api/openapi.json`과 `messageResponse.ts`다. 내용 변경은 두 파일뿐이다.
- 증거 기록: 이 문서만 바꾸는 commit으로, 검토한 내용 commit 바로 다음 commit이다.
- 설계 문서: `docs/03-analysis/task_T221-message-author-names.blueprint.md`

## 배경

2026-10-24 화면 점검에서 메시지 작성자가 `36f8493c-…` 같은 식별 번호로 보였고, 아바타에는 그 앞 두 글자가 나왔다. 백엔드 응답에 작성자 이름이 없었고, 화면에서 이름을 찾을 수단도 없었다.

사용자는 "각각 독립실행으로 개선"을 요청했다. 구현 담당은 컴파일·테스트를 돌리지 않았고, 코디네이터가 Gradle을 포함한 모든 검사를 실행했다.

## 진행 방식

- 오르카로 독립 작업 폴더(`origin/main` 기준)를 만들었다.
- 구현 담당 1명이 백엔드 테스트를 작성만 했고, 실행은 코디네이터가 했다.
- 생성물(OpenAPI, api-client)은 코디네이터가 다시 생성해 commit했다.
- 새 검토자 2명(명세 준수, 코드 품질·보안)이 읽기 전용으로 검토했다.

## 독립 검토

| 차수 | 구분 | 대상 SHA | 점수 | P0/P1/P2/P3 | 판정 |
| --- | --- | --- | ---: | --- | --- |
| 1 | 명세 준수 | `fa74c01908d60150d36386dbc25ed87a5faef6be` | 96/100 | 0/0/0/3 | 통과 |
| 1 | 코드 품질·보안 | `fa74c01908d60150d36386dbc25ed87a5faef6be` | 93/100 | 0/0/0/4 | 통과 |

품질·보안 검토의 확인 사항:

- 모듈 경계: 다른 모듈의 table을 직접 join하지 않는다. message module에는 framework 의존이 없는 port만 추가됐다.
- 조회 횟수: 메시지 목록·검색은 한 page의 작성자를 한 번에 조회한다(`WHERE id = ANY(?)`). 메시지마다 따로 조회하는 N+1 문제가 없다.
- 실시간 이벤트: 이름이 없으면 key를 빼서 null 오류를 피한다.
- 화면 출력: 표시 이름은 사용자가 입력한 값인데, 모두 일반 텍스트로 출력하며 `v-html`은 쓰지 않는다.

## 검증 (`fa74c01`, Windows, 코디네이터 직접 실행)

| 명령 | 결과 |
| --- | --- |
| `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar` | 성공 |
| `npm run lint` | 성공 |
| `npx vitest run` (`apps/web`) | 175 통과, 8 건너뜀 |
| `npm run build --workspace @discord-clone/web` | 성공 |
| `git diff --check origin/main HEAD` | 성공 |
| `npm run openapi:check` | `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163` |
| PostgreSQL 계약(임시 `postgres:16-alpine`, 실행 후 제거) | `PostgresAuthStoreTest` 5/5, `JdbcMessageStoreTest` 11/11. 실패·건너뜀 0 |
| 실제 화면 둘러보기(T221 백엔드 jar + 배포 웹 빌드) | 작성자가 "민아", 아바타가 "민아"로 보인다. 식별 번호는 보이지 않는다. |

- 화면 캡처: `C:\tmp\review-extract\ui-polish\shots-task_T221-message-author-names\`
- 새 데이터베이스 migration은 없다.

## 설계 문서와의 일치

- 일치 여부: 일치
- 어긋남: 메시지 타입이 brief가 말한 `discord-api.ts`가 아니라 `shell.ts`에 있어서, 그 파일을 고쳤다. 설계 문서에 기록돼 있다.

## 수용

- 설계 수용: 예
- 구현 수용: 예
- 검증 수용: 검토한 내용 기준으로 예

## 남은 위험·미룬 일

- P3(견고성)
  - users 행 하나가 현재 검증 규칙에 어긋나면 이름 조회에서 예외가 난다. 그러면 메시지 page 전체가 실패하고, Kafka 경로에서는 그 작성자의 메시지가 실패 보관함으로 간다. 지금은 users 입력 경로가 하나이고 검증을 거치므로 잠재 위험이다.
  - 이름 조회가 잠깐 실패해도 메시지 목록·생성 응답이 500이 된다. 이름은 부가 정보이므로, 실패 시 `null`로 처리하고 log를 남기는 편이 낫다.
- P3(정리·시험)
  - 원래 두 벌이던 `gatewayPayload`(`MessageConfiguration`, `KafkaMessagePublishedConsumer`)에 같은 분기가 하나씩 더해졌다.
  - 작성자가 없을 때 REST JSON에 `"authorDisplayName": null` key가 실제로 들어가는지 확인하는 controller test가 없다.
- 후속 작업
  - "알 수 없는 사용자"는 문자열 상수라 영어 화면에서도 한국어로 보인다. locale key로 옮겨야 한다.
  - 전송 중인 메시지는 확정되기 전까지 작성자를 `currentUser`로 표시한다. T220 병합 뒤 정리한다.
  - mention 표시는 아직 식별 번호를 보여 준다.
  - 실시간 메시지에 `clientEventId`가 없으면, REST로 불러온 메시지가 목록에서 지워지는 기존 결함이 있다. T220이 고친다.
