# T218 화면 시험 라이브러리 보안 업데이트 설계

## 승인
- 상태: 승인됨. 2026-10-24 사용자가 "라이브러리 고쳐줘"로 요청했다.
- 배경
  - 2026-10-24 새 보안 경고가 나오면서 깃허브 자동 점검의 보안 검사(qa-security)가 `main`과 모든 작업 브랜치에서 실패한다.
  - 보안 검사 요약에는 "높음 이상, 병합을 막는 경고 2건"이 찍혀 있다.

## 원인
| 라이브러리 | 현재 버전 | 영향 범위 | 경고 | 들어오는 경로 |
| --- | --- | --- | --- | --- |
| `happy-dom` | 20.9.0 | 17.0.0~21.0.3 | GHSA-834v-8h66-3h79(높음): CVE-2026-33943 수정이 불완전해 import 별칭으로 호스트 코드가 실행될 수 있음 | `apps/web` 시험 의존성(직접) |
| `undici` | 7.29.1 | 7.0.0~7.30.0 | 높음 3건: 이벤트 스트림 해석기 메모리 고갈, 무결성 검사 우회, 과다 인코딩 처리 오류 | `orval` → `@scalar/json-magic` 0.15.7이 7.29.1로 정확히 고정 |

둘 다 개발·시험 도구에만 쓰이고 운영 화면 번들에는 들어가지 않는다. 그래도 CI 보안 검사가 막히므로 고친다.

## 결정
1. `happy-dom`: `apps/web` 시험 의존성을 공식 수정 버전 `21.0.4`로 올린다(큰 버전 변경).
2. `undici`: 루트 `package.json`의 npm `overrides`로 `@scalar/json-magic` 아래의 `undici`만 `7.30.1`로 바꾼다.
   - 같은 큰 버전 안의 공식 수정판이라 호환된다.
   - `@scalar/json-magic` 자체를 0.17로 올리면 `undici` 8로 바뀌고 `orval` 내부 동작이 바뀔 수 있다. 그래서 고르지 않았다.
3. 공식 수정판이 있으므로, 이 저장소가 공식 수정이 없을 때 쓰던 직접 고친 사본(`vendor/ci-dependency-patches`) 방식은 쓰지 않는다.
4. 위험도 보통·낮음(`vitest`, `@vitest/mocker`, `serialize-javascript`)은 병합을 막지 않으므로 이번 범위에서 뺀다.

## 바뀌는 파일
- `apps/web/package.json`(`happy-dom` 21.0.4)
- `package.json`(`overrides`)
- `package-lock.json`
- 이 설계 문서
- 병합 전 증거: `docs/03-analysis/task_T218-merge-review.md`(별도 commit)

## 지켜야 할 것
- 운영 코드·백엔드·API 명세·자동 생성 client는 바꾸지 않는다.
- `orval` 생성 결과가 바뀌지 않는다. `npm run openapi:check`가 통과해야 한다.
- 화면 시험 결과가 그대로다.

## 검증 결과 (Windows, 작업 폴더 `orca/workspaces/discord/task_T218-happy-dom-security`)
- `npm audit`: 바꾸기 전 높음 2(`happy-dom`, `undici`), 보통 4, 낮음 1. 바꾼 뒤 높음 0, 보통 2, 낮음 1.
- `npm ls`: `happy-dom@21.0.4` 하나로 맞춰졌다. `undici@7.30.1 overridden`. 맞지 않는 peer 의존성 경고는 없다.
- `npm run openapi:check`: `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163`. 생성 결과가 같다.
- `npm run lint:frontend` 통과.
- `npm test`: web 164 통과·8 건너뜀, 나머지 패키지 전부 통과.
- `npm run build --workspace @discord-clone/web` 통과.
- `.\gradlew.bat test checkstyleMain checkstyleTest --continue`: `backend/modules/bot`의 `InMemoryWebhookServiceTest.sendsWebhookMessageWithExplicitSourceWhenTokenIsValid` 하나만 실패하고 나머지는 모두 통과했다.
  - 이 실패는 **원래 있던 불안정 시험**이다. T218은 백엔드 코드를 바꾸지 않았다.
  - 원인: 감사 기록을 생성 시각 역순으로 정렬하는데, 두 기록의 시각이 같으면 넣은 순서가 그대로 남는다.
  - 같은 시험만 5번 반복하면 4번 실패했다(Windows 시계 정밀도). 리눅스 CI에서는 통과해 왔다.
  - 별도 작업으로 고친다.
- `git diff --check` 통과.
- 외부 OSV 조회를 포함한 전체 보안 검사(`qa/security-gate.ps1`)는 CI가 확인한다.
