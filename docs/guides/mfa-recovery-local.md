# 로컬 MFA 복구와 패스키 관리

이 문서는 [로컬 WebAuthn MFA 가이드](mfa-webauthn-local.md)에 추가되는 TOTP 잠금 해제, TOTP 재설정, 패스키 삭제 정책을 설명합니다. 이 흐름은 loopback에서만 사용할 수 있는 개발용 identity 서비스 기능입니다.

## 보안 정책

- 복구 코드, 이메일·SMS 복구, 운영자 우회 기능은 없습니다.
- 잠금 해제와 TOTP 재설정은 본인 JWT subject, 만료되지 않은 5분 이내 토큰, 정확히 `amr=[passkey]`, 설정된 `Origin`, 원격 주소 `127.0.0.1`을 모두 요구합니다. 일반 로그인 토큰과 TOTP MFA 토큰은 사용할 수 없습니다.
- 잠금 해제는 실패 횟수와 진행 중인 TOTP challenge만 초기화합니다. 기존 secret과 이미 소비한 TOTP step은 유지합니다.
- 재설정은 새 secret을 5분 동안 암호화해 대기 상태로 저장합니다. 확인이 끝날 때까지 기존 secret이 계속 활성 상태입니다. 새 코드 확인에 성공하면 secret 교체, 대기 값 삭제, 실패 횟수 초기화가 한 번의 DB 변경으로 이뤄집니다. 만료되거나 확인에 실패하면 기존 secret이 유지됩니다.
- 마지막 패스키는 TOTP가 활성 상태이며 실패 횟수가 5 미만일 때만 삭제할 수 있습니다. 패스키가 둘 이상이면 하나를 삭제해도 이 조건을 검사하지 않습니다.

## 브라우저에서 복구하기

1. `mfa-webauthn.html`에서 로그인하고 등록된 패스키로 추가 인증합니다.
2. **패스키로 TOTP 잠금 해제**를 선택해 잠금을 해제합니다. 이 작업은 secret을 바꾸지 않습니다.
3. TOTP 기기를 잃어버렸다면 **패스키로 TOTP 재설정**을 선택합니다. 한 번 표시된 secret을 인증 앱에 등록합니다.
4. 새 secret의 현재 6자리 코드를 입력하고 **등록 확인**을 선택합니다. 확인 전까지 기존 secret이 계속 유효하고, 확인 후에는 새 secret만 유효합니다.
5. 패스키 목록에서 삭제할 credential을 선택합니다. 마지막 패스키를 지우려면 활성 TOTP가 잠기지 않은 상태여야 합니다.

| 상황 | 가능한 처리 |
| --- | --- |
| TOTP 5회 실패로 잠김, 패스키 보유 | 패스키 추가 인증 → **잠금 해제**. 이후 TOTP로 다시 인증할 수 있습니다. |
| TOTP 기기 분실, 패스키 보유 | 패스키 추가 인증 → 잠금 해제 → **재설정** → 새 코드 확인. |
| 패스키 분실, TOTP 사용 가능 | TOTP 추가 인증 후 잃어버린 패스키를 삭제하고 새 패스키를 등록합니다. 마지막 패스키 삭제는 TOTP가 잠기지 않아야 합니다. |
| 패스키와 TOTP 모두 사용 불가 | 이 로컬 데모에는 self-service 복구 수단이 없습니다. 복구 코드나 운영자 우회는 구현하지 않았습니다. |

잠금 해제·재설정 API는 `Cache-Control: no-store`를 반환합니다. 브라우저 화면은 secret을 페이지 메모리와 화면에만 표시하며, 로컬 저장소·쿠키·URL에 기록하지 않습니다. 사용자는 화면을 캡처하거나 개발자 도구 네트워크 기록을 공유하지 않도록 주의해야 합니다.

## API 요약

| 요청 | 성공 | 거부 조건 |
| --- | --- | --- |
| `POST /api/auth/mfa/totp/unlock` | `204` | token/Origin/loopback 실패 `401` 또는 `403`, TOTP 행 없음 `409`, 저장소 장애 `503` |
| `POST /api/auth/mfa/totp/reset` | `200 {"secret":"..."}` | unlock과 같은 인증 조건, 활성 TOTP 행 없음 `409`, 저장소 장애 `503` |
| `POST /api/auth/mfa/totp/confirm` | `204` | 유효한 신규 코드가 아니거나 대기 5분이 지났으면 `401` |
| `DELETE /api/auth/mfa/webauthn/credentials/{id}` | `204` | 마지막 credential을 지키는 TOTP 조건 미충족 시 `409` |

개발용 DB에 이미 `mfa_totp` 테이블이 있다면 `db/mfa-schema.sql`을 다시 적용해 `pending_secret_ciphertext`, `pending_expires_at` 열을 추가합니다. 스크립트는 `ADD COLUMN IF NOT EXISTS`를 사용하므로 기존 TOTP secret을 바꾸지 않습니다.

## 검증 명령

```bash
./gradlew :backend:services:identity:test --tests com.example.discord.identityservice.WebAuthnControllerTest \
  --tests com.example.discord.identityservice.MfaControllerTest --tests com.example.discord.identityservice.JdbcTotpStoreTest \
  --tests com.example.discord.identityservice.MfaLocalUiRuntimeTest
node --test "qa/mfa-local-ui/*.test.mjs"
bash qa/mfa-postgres-verify.sh
MFA_E2E_PLAYWRIGHT_ROOT=C:/git/discord node qa/mfa-local-ui-e2e.mjs
```

PostgreSQL 검증은 opt-in Testcontainers를 사용하며 Docker가 필요합니다. 브라우저 E2E는 Playwright가 설치된 `C:/git/discord` checkout을 사용합니다. 둘 다 완료 결과에서 실행 여부를 구분해 기록합니다.
