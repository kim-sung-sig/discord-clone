# T24 Real Backend QA Orchestration Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T24 Real Backend QA Orchestration

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | real-backend Playwright smoke는 검증 가치가 높지만 backend/database/env 준비가 수동이라 반복 실행성이 낮다. |
| Solution | PowerShell QA 오케스트레이터가 backend health, API smoke, real-backend Playwright 실행과 로그 수집을 표준화한다. |
| Function UX Effect | 개발자는 한 명령으로 실제 backend 기반 login/guild/channel/message/voice/stage 흐름을 재현할 수 있다. |
| Core Value | T23의 UI/API 정합성 검증을 로컬/CI에서 반복 가능한 품질 게이트로 승격한다. |

## Scope

- Add a reusable `qa/real-backend-e2e.ps1` orchestration harness.
- Default PostgreSQL override uses `jdbc:postgresql://127.0.0.1:5432/discord`, `dev_user`, and `dev_password`.
- Reuse `qa/api-smoke.ps1` for backend API coverage.
- Run `REAL_BACKEND_E2E=1` Playwright spec through `apps/web`.
- Capture backend, API smoke, and Playwright logs under `qa/artifacts/real-backend-e2e/`.
- Add a script contract test so the harness shape is verified without starting services.

## Out of Scope

- Provisioning Docker containers from scratch.
- Changing application datasource defaults.
- Replacing the existing Playwright config.
- Full CI provider workflow wiring.

## Success Criteria

- Harness has a documented one-command path for real backend QA.
- Harness can reuse an already-running backend or start `:backend:boot:bootRun` itself.
- API smoke and real-backend Playwright env variables are set consistently.
- Contract test verifies critical parameters and command wiring without external services.

## Failure Criteria

- Real-backend QA still requires copying env variables manually.
- Harness hides service startup failures without artifact logs.
- Script assumes PostgreSQL 15432 only and ignores the current 5432 dev database convention.
