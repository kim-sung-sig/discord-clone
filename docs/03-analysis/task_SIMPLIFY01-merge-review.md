# SIMPLIFY01 병합 검수

## 대상

- Task branch: `task_SIMPLIFY01-gateway-contract`
- Base `origin/main` SHA: `d107030bb46955d4fc534c37ca9099ebf01b7eb4`
- Reviewed content SHA: `749c5d2b8d4a8e20f0696039bb842b176ac817fc`
- Evidence artifact: 이 문서만 변경하는 reviewed content의 자식 commit
- 승인된 plan/blueprint: `docs/04-report/2026-09-21-code-simplification-plan.report.html`

## 독립 리뷰

| 구분 | Preset | 대상 SHA | 점수 | P0/P1/P2 | 판정 |
| --- | --- | --- | ---: | --- | --- |
| Spec compliance | Implementation Review | `749c5d2b8d4a8e20f0696039bb842b176ac817fc` | 98/100 | 0/0/1 | 승인 |
| Quality/Security | Principal Java Implementation + Security Review | `749c5d2b8d4a8e20f0696039bb842b176ac817fc` | 98/100 | 0/0/1 | 승인 |

두 리뷰의 P2는 동일하다. 보고서 294행이 변경 전 lambda/익명 guard 구현을 현재형으로 서술한다. 최종 런타임은 `MessageAuthorizationPolicy`와 `MessageContentModerationPolicy` 구체 정책을 사용하며, 구현·권한·트랜잭션에는 영향이 없다.

## Verification

모든 검증 대상 SHA는 `749c5d2b8d4a8e20f0696039bb842b176ac817fc`다.

- `./gradlew --version`: Gradle 8.14.3 / Java 21.0.7
- `./gradlew test --rerun-tasks`: 성공, 90 actionable tasks 실행. JUnit 전체 집계 453 tests, 60 environment-gated skipped, failures/errors 0
- `DISCORD_RUN_POSTGRES_TESTS=true ./gradlew :backend:boot:test --tests com.example.discord.message.JdbcMessageStoreTest --rerun-tasks`: 성공, XML `tests=11 skipped=0 failures=0 errors=0`
- PostgreSQL 회귀 범위: wiring 분리, 저장·조회·검색·cursor, 멱등키, outbox 원자 저장과 실패 시 전체 rollback, claim lease, retry/DLQ, requeue
- `git diff --check origin/main...749c5d2b8d4a8e20f0696039bb842b176ac817fc`: 성공
- `git merge-base --is-ancestor origin/main 749c5d2b8d4a8e20f0696039bb842b176ac817fc`: 성공
- `git status --short`: clean
- `qa/backend-style-contract.ps1`: 기존 allowlist mismatch 13건으로 실패. 동일 base에서 재현되며 이번 diff의 신규 blocking mismatch는 없음

## 변경 파일 대조

`git diff --name-only origin/main...749c5d2b8d4a8e20f0696039bb842b176ac817fc`는 blueprint의 Expected Changed Files 범위와 일치한다.

- `backend/modules/gateway/src/{main,test}/java/com/example/discord/gateway/**`
- `backend/modules/message/src/{main,test}/java/com/example/discord/message/**`
- `backend/boot/src/{main,test}/java/com/example/discord/message/**`
- `docs/04-report/2026-09-21-code-simplification-plan.report.html`

merge-review artifact 자체는 이 대조에서 제외한다. evidence commit은 `git diff --name-only 749c5d2b8d4a8e20f0696039bb842b176ac817fc..HEAD`가 이 문서 하나만 반환해야 한다.

## Blueprint Alignment

- Matches blueprint: yes
- Mismatch: 구현 전 guard 형태를 현재형으로 서술한 보고서 문구 1건(P2)
- Correct owner of fix: report

## Required Loop Action

- Update blueprint/report: 이번 merge에는 defer. 런타임 영향이 없는 비차단 P2이며 다음 보고서 정리 시 과거형으로 교정
- Update implementation: 없음
- Re-run synthesis: 없음
- Re-run verification: evidence commit 후 SHA·parent·단일 파일 diff 확인

## Acceptance

- Accepted blueprint: yes
- Accepted implementation: yes
- Accepted verification: yes
- Publishable knowledge: yes. `wiki/Backend Architecture.md`와 `log.md`에 현재 message 경계와 검증 결과를 반영함

## Residual Risk와 명시적 defer

- 보고서 294행의 변경 전 lambda/익명 guard 설명을 과거형으로 바꾸는 문서 P2를 defer한다.
- style contract의 기존 allowlist mismatch 13건은 이번 task 범위 밖이며 별도 정리 대상이다.
- 전체 suite의 다른 environment-gated 60 tests는 기본 실행에서 skip된다. 이번 변경의 PostgreSQL message 계약 11개는 별도 실측으로 모두 통과했다.
- 인증·guild·프런트엔드의 전역 단순화는 실제 실행 흐름을 별도 조사한 뒤 후속 task로 진행한다.

## 판정

Spec 및 Quality/Security 리뷰가 각각 90점 이상이고 P0/P1이 없으며, 전체 회귀와 실제 PostgreSQL 검증이 성공했다. Evidence artifact 관계를 독립 검증한 뒤 feature branch push가 가능하다.
