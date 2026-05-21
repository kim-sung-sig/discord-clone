# T164 Real Backend Browser Smoke Default Gate Plan

Date: 2026-05-21

## Goal

Make the backend-backed browser smoke a default, easy agent/developer gate instead of an environment-gated Playwright test that can be skipped by normal e2e runs.

## Scope

- Expose a root npm command for the real backend browser smoke.
- Keep the existing Playwright spec that proves login, guild/channel/message, voice, and stage behavior.
- Reuse the existing real backend e2e PowerShell harness.
- Ensure local defaults match the Docker Compose topology without manual port overrides.
- Preserve CI behavior where the backend is started separately on port 8080.

## RED

Add a contract requiring:

- `npm run e2e:real-backend`
- a cross-platform Node wrapper
- CI runtime job wiring
- real backend Playwright coverage for login, guild/channel/message, voice, and stage
