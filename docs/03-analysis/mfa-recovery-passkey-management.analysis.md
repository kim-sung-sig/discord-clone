# MFA 복구·패스키 관리 구현 handoff

## 결론

사용자가 확정한 복구 정책 Q1~Q4를 `task_MFA-backend-transfer-pr`에 구현했다. 구현·보안 독립 검수와 최종 검증을 통과했으며, 이 문서는 다음 작업자가 정책·근거·남은 범위를 빠르게 확인하기 위한 handoff다.

## 확정 정책

- TOTP 잠금 해제와 기기 교체에는 `amr=[passkey]`인 5분 이내 MFA JWT만 허용한다. 복구 코드가 없으므로 passkey를 잃은 사용자는 self-service 복구를 할 수 없다.
- 마지막 passkey는 TOTP가 활성 상태이고 실패 횟수가 5 미만일 때만 삭제한다. 나머지는 409다.
- TOTP가 5회 이상 실패하면 passkey 증명으로 명시적 unlock 요청을 해야 한다. 시간 경과 자동 해제는 없다.
- TOTP 교체는 5분 만료 pending ciphertext를 저장한다. 확인 전에는 기존 secret이 계속 유효하다. 새 secret의 코드 확인 transaction에서만 secret을 원자적으로 바꾼다.

## 구현 요약

- `JdbcTotpStore`가 행 잠금 transaction에서 unlock, pending 교체, 확인, 실패 횟수 및 소비 step을 처리한다. pending secret은 이전 secret의 소비 step과 독립적으로 검증하며, owner가 challenge를 완료하면 성공·실패 모두 challenge id와 만료를 소비한다. `challenge_created_at`은 30초 발급 제한을 유지하도록 보존한다.
- `MfaController`의 unlock/reset은 exact passkey MFA 증명, 만료, Origin, loopback 조건을 확인한다. 응답은 no-store이며 로그에는 안전한 owner/status만 기록한다.
- `WebAuthnController`는 자신의 passkey 목록 및 삭제를 제공한다. 마지막 passkey 삭제는 같은 MFA 행 잠금 아래 활성 TOTP와 실패 횟수를 검사한다. 목록 저장소 오류는 503이다.
- 로컬 UI와 한국어 가이드는 복구·삭제 단계를 안내한다. 토큰과 secret은 메모리에만 두며 사용자 입력 표시에는 `textContent`를 사용한다.
- 실제 PostgreSQL 회귀에는 마지막 passkey의 잠김·비활성·정상 상태 정책을 포함한다.

## 최종 검증

| 검증 | 결과 |
| --- | --- |
| `./gradlew :backend:services:identity:test :backend:modules:identity:test :backend:services:identity:checkstyleMain :backend:services:identity:checkstyleTest --no-daemon` | 성공 |
| `bash qa/mfa-postgres-verify.sh` | 두 PostgreSQL test suite 각각 2 tests, skipped 0, failures/errors 0. 기존 컨테이너 10개 보존, task 컨테이너 정리 확인 |
| `npm run lint:backend` | 성공 |
| `node --test qa/mfa-local-ui/*.test.mjs` | 11/11 통과 |
| `node --check` (E2E runner 및 `mfa-webauthn.js`), `git diff --check` | 통과 |
| `MFA_E2E_PLAYWRIGHT_ROOT=C:/git/discord node qa/mfa-local-ui-e2e.mjs` | 통과. 가입→TOTP→passkey→잠금→unlock→reset/교체→old secret 거부→삭제. storage 0, `JSESSIONID`만 확인, 두 포트 종료 |

브라우저 E2E 산출물은 최신 `origin/main` 기반 commit에서 실행한 `qa/artifacts/mfa-local-ui-e2e/20261020132627/summary.json`이다. E2E는 H2와 가상 authenticator를 사용했으며 실제 장치 UV 동작 증거가 아니다.

## 독립 검수

- Implementation Review: 95/100 PASS, 미해결 P0/P1/P2 없음.
- Security Review: 94/100 PASS, 미해결 P0/P1/P2 없음.
- 독립 final evidence audit: PASS. 두 리뷰의 현재 소스 hash, PostgreSQL XML, E2E summary, 이 handoff와 실행 결과를 대조했고 push/PR gate를 수락했다.
- 최종 검수 대상 source SHA256:
  - `JdbcTotpStore.java`: `d40ab54f57adb584410ab4b5a6588a8961f4fa649703b75dbd72202e5ce63692`
  - `JdbcTotpStoreTest.java`: `e89d6e83da621ac8bb13a8fc1ad8278fc83936b37c8c5580ff37ad045c911d61`
  - Blueprint: `a95f1fe5cb0cd873cfacbfe35c5394d41fd2243967f1eb0b4e6fccf295b8244b`

## 범위와 한계

구현은 `127.0.0.1` loopback, localhost Origin, 단일 JVM 로컬 데모 범위다. 운영 배포, 실제 authenticator 장치, 분산 session, 운영자 복구, 복구 코드, refresh, main 병합은 검증하거나 승인하지 않았다. main에 직접 적용하지 않는다.

## 사용한 Wiki 문맥

`index.md`, `wiki/Backend Architecture.md`, `wiki/QA Infra Operations.md`를 사용했다. `Agent Development Guide.md`는 이번 단계에서 위임 계획의 추가 지침을 확인할 때 사용하지 않았다.
