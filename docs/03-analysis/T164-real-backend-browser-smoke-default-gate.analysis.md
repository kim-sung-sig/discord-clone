# T164 Real Backend Browser Smoke Default Gate Analysis

Date: 2026-05-21

## Findings

- `apps/web/tests/e2e/real-backend.spec.ts` already covered backend login, guild/channel/message, voice token, stage, and token persistence safety.
- The Playwright test skipped by default unless `REAL_BACKEND_E2E=1`.
- `qa/real-backend-e2e.ps1` could run the full flow, but there was no root npm default gate.
- Local defaults used Postgres port 5432 even though the project Compose topology maps Postgres to 15432.
- Redis health could keep `/actuator/health` unhealthy even though this smoke does not validate Redis behavior.
- Stopping the Gradle process alone could leave the Java child process holding module jars.

## Decisions

- Add `npm run e2e:real-backend` as the default real backend browser gate.
- Keep CI backend startup on 8080, but use 18080 for local default gate runs.
- Reuse central Compose health before local backend startup.
- Disable Redis health for this smoke and leave Redis behavior to central Redis smoke tasks.
- Stop the owned Java child process when this harness starts the backend.

## Verification

- RED: `powershell -ExecutionPolicy Bypass -File qa\real-backend-browser-smoke-default.contract.ps1`
  - Failed first because `qa/real-backend-e2e.mjs` was missing.
- GREEN:
  - `powershell -ExecutionPolicy Bypass -File qa\real-backend-e2e.contract.ps1`
  - `powershell -ExecutionPolicy Bypass -File qa\real-backend-browser-smoke-default.contract.ps1`
  - `powershell -ExecutionPolicy Bypass -File qa\ci-workflow.contract.ps1`
  - `npm.cmd run e2e:real-backend -- -BackendStartupTimeoutSeconds 120`
  - `Get-NetTCPConnection -LocalPort 18080` returned no listener after cleanup.
