# CI Runtime·Security Repair Blueprint

## 문제

- `qa-runtime`은 JWT configtree와 `legacy-auth` 프로필 없이 `bootRun`하여 `AuthConfiguration`에서 기동 실패한다.
- `qa-security`는 패치 전 Nuxt와 백엔드 의존성의 high/critical OSV finding을 차단한다.

## 최소 변경

1. runtime QA에 저장소 테스트 Ed25519 key pair를 configtree로 주입하고 `postgres,legacy-auth`를 활성화한다.
2. Spring Boot 3.5.16, Netty 4.1.136.Final로 고정해 백엔드 차단 finding을 패치한다.
3. Nuxt를 4.5.2로 올리고 lockfile의 자동 패치 가능한 취약 전이 의존성을 갱신한다.

## 검증 기준

- runtime backend가 `/actuator/health`에 응답하고 `qa/real-backend-e2e.ps1 -SkipServiceStart`가 통과한다.
- `pwsh qa/security-gate.ps1`가 `SECURITY_GATE_PASS`를 출력한다.
- 기존 backend/frontend 테스트와 workflow diff 검토를 통과한다.
