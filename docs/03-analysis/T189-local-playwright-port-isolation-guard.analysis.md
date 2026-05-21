# T189 Local Playwright Port Isolation Guard Analysis

Created: 2026-05-21
PDCA Phase: Check
Slice: T189 Local Playwright Port Isolation Guard

## TDD Evidence

| Phase | Command | Result | Evidence |
| --- | --- | --- | --- |
| RED | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/playwright-port-isolation.contract.ps1` | FAIL | Failed because `qa/web-e2e-isolated.mjs` was missing. |
| GREEN | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/playwright-port-isolation.contract.ps1` | PASS | `PLAYWRIGHT_PORT_ISOLATION_CONTRACT_PASS` |
| Subagent setup | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/subagent-role-packets.contract.ps1` | PASS | `SUBAGENT_ROLE_PACKETS_CONTRACT_PASS` |
| Harness regression | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.contract.ps1` | PASS | `AGENT_HARNESS_CONTRACT_PASS` |
| Harness tool | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.ps1 -Tool playwright-port-isolation-contract` | PASS | `AGENT_HARNESS_TOOL_PASS playwright-port-isolation-contract` |
| Runtime smoke | `npm.cmd run e2e:web:isolated -- --project=chromium --workers=1 tests/e2e/app-theme.spec.ts tests/e2e/layout-compression.spec.ts` | PASS | Wrapper printed `port=53790`; Playwright reported `2 passed`. |
| Observer smoke | `npm.cmd run e2e:web:isolated -- --project=chromium --workers=1 layout-compression.spec.ts visual-smoke.spec.ts` | PASS | Wrapper printed `port=63201`; Playwright reported `2 passed`. |

## Debugging Notes

- First wrapper runtime attempt failed with `spawn EINVAL` on Windows because direct `.cmd` spawn is not accepted in this environment.
- Using `shell: true` with args removed `EINVAL` but produced Node `DEP0190` and was rejected by code-quality review because passthrough args could become shell input.
- Final wrapper avoids shell mode. When npm sets `npm_execpath`, it spawns `process.execPath` with `[npm_execpath, ...args]`, preserving argument boundaries for Playwright passthrough flags.

## Subagent Review

| Agent | Result | Follow-up |
| --- | --- | --- |
| QA/Spec Agent | CHANGES_REQUESTED | Added Runtime QA Agent packet coverage to `docs/03-tasking/subagent-role-packets.md` and `qa/subagent-role-packets.contract.ps1`. |
| Code Quality Agent | CHANGES_REQUESTED | Removed the shell command string path from `qa/web-e2e-isolated.mjs` and extended `qa/playwright-port-isolation.contract.ps1` to forbid `windowsCommand` and `shell: true`. |
| Observer/Test Agent | PASS | Ran the requested `layout-compression.spec.ts visual-smoke.spec.ts` isolated Chromium smoke. |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| Root e2e self-isolates | PASS | `package.json` points `e2e` and `e2e:web:isolated` to `node qa/web-e2e-isolated.mjs`. |
| Raw escape hatch remains | PASS | `package.json` keeps `e2e:web:raw`. |
| Playwright reuse is opt-in | PASS | `apps/web/playwright.config.ts` uses `PLAYWRIGHT_REUSE_EXISTING_SERVER === '1'`. |
| Contract covers behavior | PASS | `qa/playwright-port-isolation.contract.ps1`. |
| Subagent role setup exists | PASS | `docs/03-tasking/subagent-role-packets.md` and contract pass. |

## Residual Risks

| Risk | Impact | Decision |
| --- | --- | --- |
| Nuxt webServer shutdown can still hang after tests pass | Coordinator may need to stop the selected isolated port manually. | Accept as residual for now; T189 solves wrong-server reuse and logs the exact port. |
| `NO_COLOR` warning remains from Playwright/Nuxt environment | No functional failure; existing local warning already observed. | Track separately if warning-budget scope requires it. |
