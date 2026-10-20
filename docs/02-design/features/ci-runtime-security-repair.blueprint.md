# T193 CI Runtime·Security Repair Blueprint

## 문제와 증거

- CI runtime job은 backend, health probe, smoke가 동일 runner에서 통신하므로 loopback(`127.0.0.1`)을 써야 한다. 고정 `8080`은 충돌 가능성이 있어 job에서 동적 포트를 선택해 전달한다.
- 이전 PR run `39436118827`에서 `qa-runtime` smoke 요청이 완료되지 않았다. 로컬 PostgreSQL 재현으로 원인을 분리한 결과, `InMemoryGatewayService.appendBusEvent()`가 진행하기 전에 `JdbcGatewayEventLog.latestSequence()`가 행의 `MAX(event_sequence)`만 반환하고, `allocateSequence()`는 row insert 전 DB sequence만 증가시켜 루프가 끝나지 않았다.
- `qa-security`는 frontend 및 backend 의존성 high/critical advisory를 보고했다. Registry에 `braces` 및 `node-forge`의 공식 수정 버전이 없어서 scanner 우회 대신, 검토한 upstream 보안 수정을 담은 최소 local backport와 회귀 테스트를 사용한다.

## 승인 범위

- 사용자가 요청한 범위: 동적 loopback port, 실제 runtime smoke 성공, 보안 gate 성공.
- 금지: QA gate 비활성화, allowlist로 취약점 숨김, 관련 없는 재구성.
- 알려진 잔여 위험: OS에서 포트를 고른 뒤 backend가 bind하기 전에 다른 프로세스가 점유할 수 있는 짧은 race.

## 최소 변경

1. CI runtime job에서 loopback 포트를 한 번 선택하고 backend, health probe, smoke URL에 같은 값을 전달한다. workflow contract는 고정 `127.0.0.1:8080`을 거부한다.
2. JDBC `latestSequence()`를 저장 row의 최대값이 아니라 identity sequence의 현재 할당값과 일치시킨다. PostgreSQL 통합 회귀 테스트는 row insert 전 할당값도 확인한다.
3. API smoke 요청마다 시작/완료 로그와 finite HTTP timeout을 둬 실패 endpoint를 식별하고 무한 대기를 막는다.
4. backend와 frontend의 advisory를 실제 fixed release 또는 최소 검토된 local backport로 제거한다. Local `braces` backport는 recursive depth/AST/cycle 경계를 제한하고 호환성을 테스트한다. Local `node-forge` backport는 canonical DigestInfo DER 전체를 재인코딩해 바이트 단위로 비교하고 malformed DER, 정상 서명, parser option에 관계없는 trailing-byte 거부를 테스트한다.
5. 기존 전체 CI test, build, SBOM/OSV/npm security scan을 그대로 유지한다.

## 구현 대상

- `.github/workflows/ci.yml`, `qa/ci-workflow.contract.ps1`, browser/runtime smoke 계약
- `backend/boot/.../JdbcGatewayEventLog.java` 및 PostgreSQL 테스트
- `qa/api-smoke.ps1`, `qa/real-backend-e2e.contract.ps1`
- `backend/boot/build.gradle.kts`, `apps/web/package.json`, `apps/web/.nuxtrc`
- `vendor/ci-dependency-patches/{braces,node-forge}/`, `packages/dependency-backports/`
- root `package.json`과 `package-lock.json`
- 이 blueprint, `docs/01-plan/features/T193-ci-runtime-dynamic-port.plan.md`

## 검증 기준

- Focused PostgreSQL regression 및 backend tests 통과
- API smoke와 workflow/security-gate contracts 통과
- Node 22에서 `npm test --workspaces`, frontend production build, `npm audit --audit-level=high` 통과
- `qa/security-gate.ps1`가 `SECURITY_GATE_PASS` 출력
- workflow YAML parse, `git diff --check` 통과
- Push 후 PR CI의 `backend`, `frontend`, `qa-runtime`, `qa-security`, `qa-toolchain` 모두 성공

## 수용 경계

- `/actuator/health` 성공만으로 runtime 수용 처리하지 않는다. 실제 API smoke 완료를 요구한다.
- CI scan이 local backport를 같은 advisory ID로 계속 탐지하면 성공 처리하지 않고, dependency 해석과 scan artifact를 조사한다.
- 포트 선택 race는 현재 workflow 방식의 잔여 위험으로 기록한다. 실제 충돌이 관측되면 backend 재시도 또는 socket 상속 구조를 검토한다.
