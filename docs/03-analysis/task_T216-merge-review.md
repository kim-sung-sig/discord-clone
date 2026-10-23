# T216 병합 검토 기록

## 대상

- 작업 브랜치: `task_T216-message-pipeline-followups`
- 기준 `origin/main` SHA: `76db98d412f6af240e0b093737ed0aaea53baa9a`
- 검토한 내용 SHA: `09f90f90ddfebecab48ebf24cc11f9e60521566b`
  - `babc6fe` 구현
    - 내용이 비어 있는(`null`) 메시지를 즉시 실패 보관함으로 보낸다.
    - 재시도 집계를 바로잡는다.
    - 이벤트 기록 여부를 존재만 확인하도록 줄인다.
    - 충돌 분기 시험, 장애 재현 시험의 차이값 비교, 문서·증거 갱신
  - `09f90f9` 1차 검토 후속
    - 다시 시도할 계획이 있을 때만 재시도로 센다.
    - 재시도 없음을 직접 확인하는 시험을 추가했다.
    - 문서를 정정했다.
- 증거 기록: 이 문서만 바꾸는, 검토한 내용 commit의 바로 다음 commit이다.
- 설계 문서: `docs/03-analysis/task_T216-message-pipeline-followups.blueprint.md`

## 진행 방식

- 오르카 명령으로 독립 작업 폴더(`orca/workspaces/discord/task_T216-message-pipeline-followups`, `origin/main` 기준)를 만들었다. 원래 작업 폴더에서는 다른 에이전트가 T214를 진행 중이라 건드리지 않았다.
- 같은 작업 폴더에서 오르카 오케스트레이션(실행 `run_3770d17cf4c5`)으로 Claude 일꾼을 띄우고, 다음 순서로 릴레이했다.
  1. 구현 일꾼
  2. 독립 검토자 2명
  3. 검토 지적을 새 일꾼에게 전달
  4. 최종 검토자 2명
- 검토자는 모두 새 세션이었고 읽기 전용이었다.

## 독립 검토

| 차수 | 구분 | 대상 SHA | 점수 | P0/P1/P2 | 판정 |
| --- | --- | --- | ---: | --- | --- |
| 1 | 명세 준수 | `babc6fe` | 96/100 | 0/0/0 | 통과(P3 3) |
| 1 | 코드 품질·보안 | `babc6fe` | 93/100 | 0/0/0 | 통과(P3 5) |
| 2 | 명세 준수 | `09f90f90ddfebecab48ebf24cc11f9e60521566b` | 96/100 | 0/0/0 | **승인**(P3 3) |
| 2 | 코드 품질·보안 | `09f90f90ddfebecab48ebf24cc11f9e60521566b` | 95/100 | 0/0/0 | **승인**(P3 3) |

- 1차의 P3 8건은 모두 `09f90f9`에서 반영하거나 문서로 남겼다. 이 후속 commit은 운영 코드(재시도 집계 위치)를 바꿨기 때문에 새 검토자로 다시 검토했다.
- 검토 원문: `C:\tmp\review-extract\t216-spec-review.md`, `t216-quality-review.md`, `t216-spec-review2.md`, `t216-quality-review2.md`

## 검증 (`09f90f90ddfebecab48ebf24cc11f9e60521566b`, Windows, 코디네이터 직접 실행)

| 명령 | 결과 |
| --- | --- |
| `.\gradlew.bat test checkstyleMain checkstyleTest :backend:boot:bootJar` | 성공 |
| `npm run lint` | 성공 |
| `qa/migration-guard.contract.ps1` | `MIGRATION_GUARD_PASS` |
| `npm run openapi:check` | `OPENAPI_CONTRACT_PASS operations=110 generatedFiles=162`(API 변화 없음) |
| `npm test`(전체 패키지) | web 161 통과·8 건너뜀, 나머지 전부 통과 |
| `npm run build --workspace @discord-clone/web` | 성공 |
| PostgreSQL 계약(독립 `postgres:16-alpine`, 실행 후 제거) | 이벤트 기록 존재 확인 2, Guild 서비스 6, Guild 저장소 7, 모더레이션 12. 실패·건너뜀 0 |
| Kafka 장애 재현(`DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS=true`) | 소비 8, 설정 24, 복구 실패 4, 임대 5, 통합 11, 일정 1. 실패·건너뜀 0 |
| `git diff --check` | 성공 |
| 컨트롤러·`docs/api/openapi.json`·생성 client 변경 | 없음(T214와 병합 충돌 방지 조건 충족) |

## 설계 문서와의 일치

- 일치 여부: 일치
- 어긋남: 없음

## 수용

- 설계 수용: 예
- 구현 수용: 예
- 검증 수용: 검토한 내용 기준으로 예
- 위키에 남길 지식
  - 재시도 집계는 "다음 시도 일정이 잡힌 실패"만 센다.
  - 내용이 비어 있는 메시지는 즉시 실패 보관함으로 간다.
  - 이벤트 기록 여부는 존재만 확인한다.
  - 오르카 작업 폴더와 릴레이 루프로 작업을 진행하는 방식

## 남은 위험·미룬 일

- P3(문서): T216 설계 문서의 증거 경로 다섯 곳(42, 162~163, 171, 179~180, 186행)에 제어 문자(TAB, FF, 줄바꿈)가 섞여 경로가 깨져 있다. `09f90f9`에서 생겼다. 내용 검토가 끝난 뒤라 이번에는 고치지 않았고, 다음 작업에서 정정한다.
- P3(문서): 관측 설계 문서 194행이 이제 쓰지 않는 `deliveryAttempt<=2` 방식을 현재형으로 적고 있다.
- P3(시험)
  - 실패 보관함 발행 실패 경계 시험이 IAE만 사용해 판별력이 약하다.
  - 재시도 가능한 실패 뒤 새 주기의 집계는 시험되지 않았다.
- P3(코드): 재시도 간격 숫자 `1_000L`이 두 곳에 중복돼 있다.
- 이관
  - 주제(topic)별 실패 보관함 분리
  - 동시 전달 시 `processed` 중복 집계
  - 실패 보관함 발행이 실패한 뒤 즉시 재전달되는 경우의 집계 정의
