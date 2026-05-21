# T164 Real Backend Browser Smoke Default Gate Report

Date: 2026-05-21

## Result

Completed. The real backend browser smoke is now exposed as a root npm gate:

```powershell
npm.cmd run e2e:real-backend
```

The gate bootstraps central Compose resources, starts the backend on local port 18080, runs API smoke, runs the Playwright real-backend flow, and cleans up the owned backend Java child process.

## Verified Behavior

- Backend signup/login through `/login`.
- Guild/channel/message creation through the real backend.
- Voice token provider output.
- Stage state.
- Client storage does not persist access tokens.

## Verification Commands

```powershell
powershell -ExecutionPolicy Bypass -File qa\real-backend-e2e.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\real-backend-browser-smoke-default.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\ci-workflow.contract.ps1
npm.cmd run e2e:real-backend -- -BackendStartupTimeoutSeconds 120
```

All checks passed. The final run produced `REAL_BACKEND_E2E_PASS` and cleaned port 18080.

## Security Review

The smoke continues to assert that access tokens are not persisted in localStorage, sessionStorage, cookies, or Playwright-visible cookie state. Redis health is disabled only for this Postgres/browser gate; Redis behavior remains covered by central Redis smoke.
