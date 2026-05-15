# Agent Team Operating Model

작성일: 2026-05-15  
목적: Discord clone 작업을 구현 계획, 구현, 리뷰, QA로 분리해 컨텍스트 낭비와 병렬 작업 충돌을 줄인다.

## 1. Core Principle

코디네이터는 전체 맥락과 순서만 관리한다. 각 에이전트는 필요한 문서, 수정 가능 경로, 금지사항, 검증 명령만 받는다.

기본 규칙:

- 한 에이전트는 한 bounded scope만 소유한다.
- 구현 에이전트와 리뷰/QA 에이전트는 분리한다.
- shared state나 같은 파일을 동시에 수정하는 작업은 병렬화하지 않는다.
- 실패 로그 전문을 공유하지 않는다. artifact 경로와 핵심 stderr 30~80줄만 전달한다.
- 에이전트 결과는 `P0/P1/P2`, 재현 명령, 수정 파일, 재실행 게이트 중심으로 받는다.

## 2. Coordinator Role

`Coordinator`는 사람이 보는 전체 흐름을 보존한다.

소유:

- task 선택과 우선순위
- agent context packet 작성
- 병렬/순차 판단
- diff 통합
- 최종 commit gate

하지 않는 일:

- 모든 파일을 직접 읽어 전체 코드를 장기 기억에 보관하지 않는다.
- 구현 에이전트의 작업을 중복 구현하지 않는다.
- 리뷰 에이전트의 P0/P1을 무시하고 다음 task로 넘어가지 않는다.

## 3. Standard Context Packet

모든 에이전트에게 아래 형식으로 전달한다.

```text
Task ID:
Goal:
Required docs:
Allowed write paths:
Read-only context paths:
Forbidden changes:
Expected tests:
Expected artifacts:
Return format:
```

예시:

```text
Task ID: T28 PWA & Mobile Web Shell
Goal: Add installable PWA metadata and mobile shell QA without breaking desktop Nuxt shell.
Required docs:
- docs/01-plan/features/T27-multi-platform-frontend-architecture.plan.md
- docs/02-design/features/T27-multi-platform-frontend-architecture.design.md
- docs/superpowers/plans/2026-05-15-multi-platform-frontend-surfaces.md
Allowed write paths:
- apps/web/nuxt.config.ts
- apps/web/public/**
- apps/web/tests/e2e/pwa-mobile.spec.ts
- apps/web/tests/components/platform-contracts.test.ts
Forbidden changes:
- Do not import Tauri/native APIs into apps/web components.
- Do not persist access tokens in localStorage/sessionStorage.
- Do not change backend APIs unless explicitly assigned.
Expected tests:
- npm run test --workspace @discord-clone/web -- --run
- npm run e2e --workspace @discord-clone/web
- npm run build --workspace @discord-clone/web
Return format:
- status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- changed files
- tests run and result
- residual risks
```

## 4. Backend Team

### Backend Coordinator

소유:

- backend task packet 작성
- `docs/01-plan/features`, `docs/02-design/features`, `docs/03-analysis` 정합성
- backend agent 간 파일 충돌 조정

### Domain Core Agent

소유:

- `backend/modules/{context}/src/main`
- `backend/modules/{context}/src/test`
- domain model, domain service, repository port, pure unit test

금지:

- `backend/boot` adapter 직접 수정
- 다른 bounded context 리팩터링
- DB migration이나 공개 API shape 변경

### Boot API Agent

소유:

- `backend/boot/src/main`
- `backend/boot/src/test`
- Spring MVC controller, configuration, auth resolver, JDBC adapter, Flyway wiring

금지:

- domain invariant를 controller에 구현
- domain test 없이 runtime adapter만 구현

### Backend QA Agent

소유:

- `qa/api-smoke.ps1`
- `qa/real-backend-e2e.ps1`
- `qa/real-backend-e2e.contract.ps1`
- backend/runtime artifact 확인

검증 명령:

```powershell
.\gradlew.bat test
.\gradlew.bat :backend:boot:test
powershell -NoProfile -ExecutionPolicy Bypass -File qa/api-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.ps1
```

## 5. Frontend And Platform Team

### Frontend Orchestrator

소유:

- frontend task packet 작성
- T27~T30 화면/플랫폼 순서 유지
- `docs/03-analysis`, `docs/04-report`, `docs/05-feedback` 산출물 정리

### Shared Contract Agent

소유:

- `packages/ui-contracts`
- `packages/platform-shell`
- `packages/api-client`
- `packages/design-tokens`

금지:

- 플랫폼별 UI 구현
- web/native API adapter 직접 구현

### Nuxt Web Baseline Agent

소유:

- `apps/web/pages`
- `apps/web/components`
- `apps/web/composables`
- `apps/web/stores`
- `apps/web/services`
- `apps/web/tests`

금지:

- Tauri/native API 직접 import
- 플랫폼별 API/error/auth fork

### PWA Mobile Agent

소유:

- `apps/web/nuxt.config.ts`
- `apps/web/public/manifest.webmanifest`
- `apps/web/server`
- `apps/web/tests/e2e/pwa-mobile.spec.ts`
- mobile viewport CSS and interaction tests

성공 기준:

- mobile single-pane IA가 channel/chat/member/voice 접근성을 유지한다.
- PWA manifest/service worker가 테스트로 검증된다.
- desktop Nuxt shell 회귀가 없다.

### Tauri Desktop Agent

소유:

- `apps/desktop`
- `apps/desktop/src-tauri`
- desktop capability adapter

금지:

- `apps/web` 내부에 Tauri API 직접 결합
- 과도한 Tauri allowlist 권한

### Expo Candidate Agent

소유:

- `docs/02-design/features/T30-native-mobile-decision.design.md`
- `apps/mobile` only after decision approval
- native navigation and capability spike

기본 판단:

- 다음 릴리스에 native-only capability가 2개 이상 필요하지 않으면 `PWA-first/native-later`를 유지한다.

## 6. Review And QA Team

### Spec Compliance Agent

입력:

- task Plan/Design
- 변경 파일 목록
- 테스트 결과 요약

차단 조건:

- 성공/실패 기준과 구현이 1:1 대응하지 않음
- UI/API 권한 불일치
- 보안/권한 시나리오 누락
- task scope 밖 기능 추가

### Code Quality Agent

입력:

- `git diff --stat`
- `git diff`
- 관련 테스트 파일

차단 조건:

- bounded context 경계 위반
- 인증/권한/token storage 결함
- race/idempotency 결함
- persistence 정합성 결함
- native/platform API가 shared contract를 우회

### Runtime QA Agent

입력:

- 실행 명령
- 환경값
- artifact 경로
- 핵심 실패 로그

검증 명령:

```powershell
.\gradlew.bat test
npm run test --workspace @discord-clone/web -- --run
npm run build --workspace @discord-clone/web
npm run e2e --workspace @discord-clone/web
powershell -NoProfile -ExecutionPolicy Bypass -File qa/api-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.ps1
```

### Harness/CI Agent

소유:

- `qa/*.ps1`
- `.github/workflows/ci.yml`
- QA artifact contract

검증 명령:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/toolchain-warning-scan.ps1
```

## 7. Parallelization Rules

병렬 가능:

- 서로 다른 bounded context의 순수 domain 작업
- domain unit test 보강과 QA script contract 보강
- shared contract test 작성과 문서 정리
- PWA viewport e2e 초안과 Tauri capability matrix 설계
- Expo decision criteria 작성과 QA script contract 검토

순차 필요:

- DB migration -> JDBC adapter -> controller test -> API smoke
- permission/auth 변경 -> 모든 write API 영향 검토 -> real-backend smoke
- gateway/event contract 변경 -> REST write 검증 -> frontend/gateway E2E
- `packages/*` 계약 확정 -> PWA/Tauri/Expo 구현
- `apps/web` baseline green -> Tauri shell packaging
- PWA mobile IA 검증 -> Expo product track 승격

## 8. Per-Task Lifecycle

1. `Coordinator`: task packet 작성
2. `Implementer Agent`: TDD로 구현하고 자기 테스트 실행
3. `Spec Compliance Agent`: Plan/Design 대비 검토
4. `Implementer Agent`: spec gap 수정
5. `Code Quality Agent`: diff 품질 검토
6. `Implementer Agent`: P0/P1 수정
7. `Runtime QA Agent`: 관련 QA 실행
8. `Coordinator`: analysis/report/feedback 문서 정리
9. `Coordinator`: commit

## 9. Commit Gate

커밋 전 최소 기준:

- Plan/Design 성공 기준과 구현/테스트가 대응한다.
- 관련 backend test 또는 frontend test가 통과한다.
- platform task는 기존 web baseline을 깨지 않는다.
- Playwright 또는 real-backend QA가 필요한 task는 artifact를 남긴다.
- P0/P1 review finding이 남아 있지 않다.
- `docs/03-analysis` 또는 `docs/04-report`에 검증 결과가 기록된다.

## 10. Recommended Next Use

T27 이후 작업은 아래처럼 시작한다.

```text
Coordinator:
  Task ID: T27-Task1 Shared UI And Platform Contracts
  Implementer: Shared Contract Agent
  Reviewer 1: Spec Compliance Agent
  Reviewer 2: Code Quality Agent
  QA: Runtime QA Agent for npm workspace tests
```

T28부터는 `PWA Mobile Agent`, T29는 `Tauri Desktop Agent`, T30은 `Expo Candidate Agent`를 사용한다.
