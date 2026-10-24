# T218 병합 검토 기록

## 대상
- 작업 브랜치: `task_T218-happy-dom-security`
- 기준 `origin/main` SHA: `3a362615b513b4d73937352bd985b2886b65584b`
- 검토한 내용 SHA: `b1334ae854f1abd08b1177e804b4e0779ca38d06`
- 증거 기록: 이 문서만 바꾸는, 검토한 내용 commit의 바로 다음 commit이다.
- 설계 문서: `docs/03-analysis/task_T218-happy-dom-security.blueprint.md`

## 독립 검토 (오르카 오케스트레이션 실행 `run_9941f232a5dd`, 새 세션 검토자, 읽기 전용)

| 구분 | 대상 SHA | 점수 | P0/P1/P2 | 판정 |
| --- | --- | ---: | --- | --- |
| 명세 준수 | `b1334ae854f1abd08b1177e804b4e0779ca38d06` | 92/100 | 0/0/0 | **승인**(P3 4) |
| 코드 품질·보안 | `b1334ae854f1abd08b1177e804b4e0779ca38d06` | 93/100 | 0/0/0 | **승인**(P3 4) |

- 두 검토자가 다시 확인한 것
  - `npm audit`에서 위험도 높음이 0건이다.
  - `npm ls`에서 `happy-dom@21.0.4`로 하나로 맞춰졌고, `undici` 덮어쓰기는 `@scalar/json-magic` 아래 사본에만 걸렸다. `jsdom`의 7.30.1과 `nuxt`의 8.12.3은 그대로다.
  - 잠금 파일 변경은 `happy-dom`, `buffer-image-size`, 중첩 `undici` 제거뿐이고, 모두 무결성 값이 있다.
  - 화면 시험(web 164 통과, 8 건너뜀은 원래 있던 Redis·Postgres 조건부 시험), API 명세 검사, 코드 검사가 통과한다.
  - OSV 전체 조회에서 높음·심각 경고가 남아 있지 않다. 그래서 CI 보안 검사는 통과할 것으로 본다.
- `happy-dom` 21의 유일한 호환성 변화(CSS·iframe 불러오기 기본 끔)는 현재 화면 시험과 관계없다.
- 검토 원문: `C:\tmp\review-extract\t218-spec-review.md`, `t218-quality-review.md`

## 설계 문서 정정 (검토 지적, 문서만 해당)
- `undici` 경고 3건은 모두 "높음"이 아니다. 실제로는 높음 1건과 보통 2건이다.
- CI 보안 검사의 "막는 경고 2건"은 `happy-dom` 경고가 두 번 집계된 것이다. `undici`가 아니다. 그래도 `undici` 높음 1건은 `npm audit`에 잡히므로 함께 고친 판단은 그대로 유효하다.
- 설계 문서에 적힌 "바꾸기 전 `npm audit` 수치"는 검토 시점에 그대로 재현되지 않았다. 경고 데이터베이스가 계속 바뀌기 때문이다.

## 검증 (`b1334ae854f1abd08b1177e804b4e0779ca38d06`, Windows)

| 명령 | 결과 |
| --- | --- |
| `npm audit` | 높음 0, 보통 2(`vitest`, `@vitest/mocker`), 낮음 1(`serialize-javascript`) |
| `npm run openapi:check` | `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163`(생성 결과 같음) |
| `npm run lint:frontend` | 통과 |
| `npm test` | web 164 통과·8 건너뜀, 나머지 패키지 전부 통과 |
| `npm run build --workspace @discord-clone/web` | 통과 |
| `.\gradlew.bat test checkstyleMain checkstyleTest --continue` | `bot` 모듈의 원래 있던 불안정 시험 1건(`InMemoryWebhookServiceTest`, 같은 시각 정렬 순서 문제, Windows에서 5번 중 4번 실패) 말고는 모두 통과. T218은 백엔드를 바꾸지 않았다. |
| `git diff --check` | 통과 |

## 설계 문서와의 일치
- 일치 여부: 일치. 위 "정정"은 설명 문장만 해당하고, 결정과 변경 내용은 그대로다.

## 수용
- 설계 수용: 예
- 구현 수용: 예
- 검증 수용: 검토한 내용 기준으로 예. 외부 OSV를 포함한 전체 보안 검사는 CI가 최종 확인한다.

## 남은 위험·미룬 일
- 덮어쓰기(`overrides`)를 지울 조건: `orval`이 `@scalar/json-magic` 0.16.1 이상(수정된 `undici` 사용)을 쓰게 되면 지운다. 지금 덮어쓰기에는 버전 조건이 없다.
- `openapi:check`는 `undici`의 원격 호출 경로를 쓰지 않는다. 로컬 파일만 읽기 때문이다.
- 위험도 보통·낮음 경고(`vitest`, `@vitest/mocker`, `serialize-javascript`)는 따로 갱신한다.
- CI의 OSV 검사가 중첩된 `undici`를 놓친 점은 범위 밖 관찰로 남긴다.
- `bot` 모듈 불안정 시험은 별도 작업으로 고친다. 같은 시각일 때 넣은 순서의 역순으로 정렬하면 된다.
