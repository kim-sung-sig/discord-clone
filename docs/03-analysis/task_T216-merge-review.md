# T216 병합 검토 기록

## 대상

- 작업 브랜치: `task_T216-message-pipeline-followups`
- 기준 `origin/main` SHA: `3a362615b513b4d73937352bd985b2886b65584b` (T214 오류 응답 통일 병합 후)
- 검토한 내용 SHA: `82c7aa9579c1ae50451be105365eaaa2af528085`
  - `babc6fe` 구현
    - 내용 없는(`null`) 메시지를 즉시 실패 보관함으로 보낸다.
    - 재시도 집계를 바로잡는다.
    - 이벤트 기록 여부를 존재만 확인하도록 줄인다.
    - 충돌 분기 시험, 장애 재현 시험의 차이값 비교, 문서·증거 갱신
  - `09f90f9` 1차 검토 후속
    - 다시 시도할 계획이 있을 때만 재시도로 센다.
    - 재시도 없음을 직접 확인하는 시험을 추가했다.
    - 문서를 정정했다.
  - `4bf0606` 예전 기준(`76db98d`)으로 쓴 병합 검토 기록. 이 문서로 교체됐다.
  - `82c7aa9` T214가 병합된 `origin/main`을 합친 commit. 겹치는 파일이 없고 충돌도 없었다.
- 증거 기록: 이 문서만 바꾸는, 검토한 내용 commit의 바로 다음 commit이다.
- 설계 문서: `docs/03-analysis/task_T216-message-pipeline-followups.blueprint.md`

## 진행 방식

- 오르카 명령으로 독립 작업 폴더(`orca/workspaces/discord/task_T216-message-pipeline-followups`, `origin/main` 기준)를 만들었다. 원래 작업 폴더에서는 다른 에이전트가 T214를 진행 중이라 건드리지 않았다.
- 오르카 오케스트레이션(실행 `run_3770d17cf4c5`)으로 같은 작업 폴더에서 Claude 일꾼을 띄우고, 다음 순서로 릴레이했다.
  1. 구현 일꾼
  2. 검토자 2명
  3. 검토 지적을 새 일꾼에게 전달
  4. 최종 검토자 2명
  5. `main` 병합 뒤 검토자 2명
- 검토자는 모두 새 세션이었고 읽기 전용이었다.

## 독립 검토

| 차수 | 구분 | 대상 SHA | 점수 | P0/P1/P2 | 판정 |
| --- | --- | --- | ---: | --- | --- |
| 1 | 명세 준수 | `babc6fe` | 96/100 | 0/0/0 | 통과(P3 3) |
| 1 | 코드 품질·보안 | `babc6fe` | 93/100 | 0/0/0 | 통과(P3 5) |
| 2 | 명세 준수 | `09f90f9` | 96/100 | 0/0/0 | 통과(P3 3) |
| 2 | 코드 품질·보안 | `09f90f9` | 95/100 | 0/0/0 | 통과(P3 3) |
| 3 | 명세 준수 | `82c7aa9579c1ae50451be105365eaaa2af528085` | 95/100 | 0/0/0 | **승인**(P3 1) |
| 3 | 코드 품질·보안 | `82c7aa9579c1ae50451be105365eaaa2af528085` | 95/100 | 0/0/0 | **승인**(P3 1) |

- 3차 검토는 병합 결과를 확인했다.
  - 병합 commit의 트리는 자동 병합 결과와 같다. T214와 T216 양쪽 변경이 그대로 보존됐다.
  - T214의 전역 오류 처리기는 HTTP 요청에만 적용된다. 그래서 Kafka 수신, 실패 보관함, 재시도 집계, 이벤트 기록 확인에는 닿지 않는다.
  - `IllegalArgumentException`을 "되돌릴 수 없는 입력 오류"로 보는 뜻도 두 작업이 같다(HTTP는 400, Kafka는 즉시 실패 보관함).
- 검토 원문: `C:\tmp\review-extract\t216-{spec,quality}-review.md`, `…-review2.md`, `…-review3.md`

## 검증 (`82c7aa9579c1ae50451be105365eaaa2af528085`, Windows, 코디네이터 직접 실행)

| 명령 | 결과 |
| --- | --- |
| `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar` | 성공 |
| `npm run lint` | 성공 |
| `qa/migration-guard.contract.ps1` | `MIGRATION_GUARD_PASS` |
| `npm run openapi:check` | `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=163`. T216에 의한 API 변화는 없다. 163개는 T214 생성물을 포함한 수다. |
| `npm test`(전체 패키지) | web 164 통과·8 건너뜀, 나머지 전부 통과 |
| `npm run build --workspace @discord-clone/web` | 성공 |
| PostgreSQL 계약(독립 `postgres:16-alpine`, 실행 후 제거) | 이벤트 기록 존재 확인 2, Guild 서비스 6, Guild 저장소 7, 모더레이션 12. 실패·건너뜀 0 |
| Kafka 장애 재현(`DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS=true`) | 소비 8, 설정 24, 복구 실패 4, 임대 5, 통합 11, 일정 1. 실패·건너뜀 0 |
| `git diff --check origin/main HEAD` | 성공 |
| `git merge-base --is-ancestor origin/main HEAD` | 성공 |
| 컨트롤러·`docs/api/openapi.json`·생성 client에 대한 T216 변경 | 없음 |

## 설계 문서와의 일치

- 일치 여부: 일치
- 어긋남: 없음

## 수용

- 설계 수용: 예
- 구현 수용: 예
- 검증 수용: 검토한 내용 기준으로 예
- 위키에 남길 지식
  - 재시도 집계는 "다음 시도 일정이 잡힌 실패"만 센다.
  - 내용 없는 메시지는 즉시 실패 보관함으로 간다.
  - 이벤트 기록 여부는 존재만 확인한다.
  - 오르카 작업 폴더와 릴레이 루프로 작업을 진행하는 방식

## 남은 위험·미룬 일

- P3(문서)
  - T216 설계 문서의 증거 경로 다섯 곳(42, 162~163, 171, 179~180, 186행)에 제어 문자(TAB, FF, 줄바꿈)가 섞여 경로가 깨져 있다.
  - 관측 설계 문서 194행이 이제 쓰지 않는 `deliveryAttempt<=2` 방식을 현재형으로 적고 있다.
  - 장애 매트릭스 73행과 확장 증거 JSON 5행이 설정 시험 수를 22로 적고 있다(실제 24, 74.946초).
- P3(시험)
  - 실패 보관함 발행 실패 경계 시험이 IAE만 사용해 판별력이 약하다.
  - 재시도 가능한 실패 뒤 새 주기의 집계는 시험되지 않았다.
- P3(코드): 재시도 간격 숫자 `1_000L`이 두 곳에 중복돼 있다.
- 이관
  - 주제(topic)별 실패 보관함 분리
  - 동시 전달 시 `processed` 중복 집계
  - 실패 보관함 발행이 실패한 뒤 즉시 재전달되는 경우의 집계 정의
