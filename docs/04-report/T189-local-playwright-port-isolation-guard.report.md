# T189 Local Playwright Port Isolation Guard Report

Created: 2026-05-21
PDCA Phase: Report
Slice: T189 Local Playwright Port Isolation Guard

## Summary

T189 made root web e2e self-isolating and changed Playwright server reuse to explicit opt-in. This prevents local browser QA from silently attaching to an unrelated server on port `3000`.

## Delivered

- Added `qa/web-e2e-isolated.mjs`.
- Added `qa/playwright-port-isolation.contract.ps1`.
- Updated root `package.json` scripts:
  - `e2e`
  - `e2e:web:isolated`
  - `e2e:web:raw`
- Updated `apps/web/playwright.config.ts` to use `PLAYWRIGHT_REUSE_EXISTING_SERVER=1` for reuse.
- Updated agent harness contract wiring.
- Added project-local subagent role packets and contract because the user requested explicit subagent setup.

## Test Evidence

- `qa/playwright-port-isolation.contract.ps1` -> PASS
- `qa/subagent-role-packets.contract.ps1` -> PASS
- `qa/agent-harness.contract.ps1` -> PASS
- `qa/agent-harness.ps1 -Tool playwright-port-isolation-contract` -> PASS
- `npm.cmd run e2e:web:isolated -- --project=chromium --workers=1 tests/e2e/app-theme.spec.ts tests/e2e/layout-compression.spec.ts` -> PASS, `2 passed`
- `npm.cmd run e2e:web:isolated -- --project=chromium --workers=1 layout-compression.spec.ts visual-smoke.spec.ts` -> PASS, `2 passed`

## Review Closure

- QA/Spec Agent requested Runtime QA role packet coverage; added and contract-verified.
- Code Quality Agent rejected the intermediate shell command implementation; replaced it with `process.execPath` plus `npm_execpath` so passthrough test args remain structured.
- Observer/Test Agent required an additional visual smoke run; completed on isolated port `63201`.

## Residual Risks

- The wrapper reports its isolated port, but Playwright/Nuxt webServer shutdown can still leave the parent command waiting after tests pass. Manual targeted cleanup may still be required until a deeper webServer shutdown fix is implemented.
- This task does not re-enable deferred CI operations/security jobs.
