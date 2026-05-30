# T165 Real LiveKit Media Smoke Analysis

Date: 2026-05-21

## RED Evidence

- `pwsh qa/livekit-media-smoke.contract.ps1` failed because the Playwright smoke spec was missing.
- The first default e2e run failed because `livekit-client/dist/livekit-client.umd.min.js` is not exported by the package.
- The first real smoke run failed at signup because the generated username contained hyphens.
- The second real smoke run failed at LiveKit connection because the page used `about:blank`; loading from the LiveKit HTTP origin fixed the browser signal request path.

## GREEN Evidence

- `pwsh qa/livekit-media-smoke.contract.ps1` passed.
- `powershell -ExecutionPolicy Bypass -File qa\livekit-media-smoke.contract.ps1` passed.
- `npm run e2e -w apps/web -- livekit-media.spec.ts` skipped by default.
- With local LiveKit and backend `media-livekit` enabled, `pwsh qa/livekit-media-smoke.ps1` passed: 1 Playwright test passed.
- `npm run lint:frontend` passed.

## Security Review

The smoke uses synthetic video and does not request camera or microphone permissions. The runbook explicitly forbids committing LiveKit secrets or copying issued JWTs into logs/artifacts.
