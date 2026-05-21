# T189 Local Playwright Port Isolation Guard Design

Created: 2026-05-21
PDCA Phase: Design
Slice: T189 Local Playwright Port Isolation Guard

## Architecture Decision

Use a Node wrapper at `qa/web-e2e-isolated.mjs` as the default root web e2e entrypoint.

The wrapper:

- Allocates an available local port with `node:net`.
- Sets `CI=1`, `NUXT_DEV_PORT`, `PLAYWRIGHT_BASE_URL`, and `PLAYWRIGHT_REUSE_EXISTING_SERVER=0`.
- Runs the existing web workspace Playwright command with passthrough arguments.
- Prints `[web-e2e-isolated] port=<port>` for log diagnosis.
- Uses Windows shell invocation only through a quoted command string to avoid Node `DEP0190`.

`apps/web/playwright.config.ts` remains the web workspace Playwright config, but existing-server reuse is now explicit:

```ts
const reuseExistingServer = process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === '1'
```

## Allowed Write Paths

- `apps/web/playwright.config.ts`
- `package.json`
- `qa/web-e2e-isolated.mjs`
- `qa/playwright-port-isolation.contract.ps1`
- `qa/agent-harness.ps1`
- `qa/agent-harness.contract.ps1`
- `docs/01-plan/features/T189-local-playwright-port-isolation-guard.plan.md`
- `docs/02-design/features/T189-local-playwright-port-isolation-guard.design.md`
- `docs/03-analysis/T189-local-playwright-port-isolation-guard.analysis.md`
- `docs/04-report/T189-local-playwright-port-isolation-guard.report.md`
- `docs/05-feedback/T189-local-playwright-port-isolation-guard.feedback.md`
- `docs/03-tasking/subagent-role-packets.md`
- `qa/subagent-role-packets.contract.ps1`

## Forbidden Changes

- Do not modify product UI behavior.
- Do not stage unrelated dirty files.
- Do not push while CI risk remains unresolved.
- Do not remove raw web e2e access; keep an explicit escape hatch.

## Verification Commands

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa/subagent-role-packets.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/playwright-port-isolation.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.ps1 -Tool playwright-port-isolation-contract
npm.cmd run e2e:web:isolated -- --project=chromium --workers=1 tests/e2e/app-theme.spec.ts tests/e2e/layout-compression.spec.ts
```

## Residual Risk Boundary

This task prevents stale local server reuse. It does not fully solve Nuxt/Playwright webServer child process shutdown hangs. If the e2e command passes but the wrapper process waits for webServer shutdown, the selected port in the log allows targeted cleanup.
