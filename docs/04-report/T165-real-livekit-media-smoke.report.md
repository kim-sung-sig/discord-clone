# T165 Real LiveKit Media Smoke Report

Date: 2026-05-21

## Result

Completed.

## Changes

- Added `apps/web/tests/e2e/livekit-media.spec.ts`.
- Added `qa/livekit-media-smoke.ps1`.
- Added `qa/livekit-media-smoke.contract.ps1`.
- Added `docs/runbooks/livekit-media-smoke.md`.
- Added optional `livekit` Docker Compose service under the `media-livekit` profile.
- Added `livekit-client` to the web workspace dev dependencies.
- Added a CI contract check for the LiveKit media smoke.

## Verification

```powershell
pwsh qa/livekit-media-smoke.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\livekit-media-smoke.contract.ps1
npm run e2e -w apps/web -- livekit-media.spec.ts
npm run lint:frontend
```

All passed. The default Playwright run skipped the gated media smoke.

```powershell
$env:LIVEKIT_MEDIA_SMOKE='1'
$env:REAL_BACKEND_BASE_URL='http://127.0.0.1:8080'
$env:LIVEKIT_URL='ws://127.0.0.1:7880'
pwsh qa/livekit-media-smoke.ps1
```

Passed with local LiveKit and backend `media-livekit` profile: 1 Playwright test passed.
