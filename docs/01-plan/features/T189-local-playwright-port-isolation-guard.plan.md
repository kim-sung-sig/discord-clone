# T189 Local Playwright Port Isolation Guard Plan

Created: 2026-05-21
PDCA Phase: Plan
Slice: T189 Local Playwright Port Isolation Guard
Type: qa-infra
Priority: P0

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Local Playwright can silently reuse an unrelated server on `localhost:3000`, producing false failures or false passes. |
| Solution | Make root web e2e self-isolate on a free local port and make Playwright server reuse explicit opt-in only. |
| Function UX Effect | Agents can run browser smoke without manually selecting a clean port. |
| Core Value | Browser QA evidence points at the current checkout instead of a stale unrelated local app. |

## Scope

- Add `qa/web-e2e-isolated.mjs`.
- Add `qa/playwright-port-isolation.contract.ps1`.
- Update root `package.json` e2e scripts.
- Update `apps/web/playwright.config.ts` reuse behavior.
- Update `qa/agent-harness.ps1` and contract wiring.
- Record subagent role packet setup because the user requested this before continuing.

## Out of Scope

- Rewriting Playwright internals.
- Solving every Nuxt webServer process hang.
- Removing the raw `apps/web` Playwright command.
- Committing Playwright output artifacts.

## Success Criteria

- Contract fails before isolated wrapper exists.
- Contract passes after implementation.
- Root `npm run e2e` delegates to the isolated wrapper.
- Raw web e2e remains available as an explicit escape hatch.
- `reuseExistingServer` is true only when `PLAYWRIGHT_REUSE_EXISTING_SERVER=1`.
- A focused e2e run prints the selected isolated port and passes without Node shell-args warnings.

## Failure Criteria

- Root e2e still silently reuses `localhost:3000`.
- Wrapper hard-codes a fixed port.
- Wrapper emits Node `DEP0190` shell-args warning.
- Agent harness does not expose the new contract.
- Subagent review reports unresolved P0/P1 findings.
