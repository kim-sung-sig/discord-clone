# T57 Process Tree Cleanup Helper Plan

Created: 2026-05-21
PDCA Phase: Plan
Slice: T57 Process Tree Cleanup Helper
Type: qa-infra
Priority: P0

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Real-backend and restore QA harnesses can leave backend child processes or port listeners alive after a failure or Playwright server hang. |
| Solution | Add a shared PowerShell cleanup helper and wire existing harnesses to it through bounded, command-line-guarded process cleanup. |
| Function UX Effect | Future agents can run QA harnesses with less manual port cleanup and with a contract that prevents broad process killing. |
| Core Value | Repeatable QA loops that clean up only owned or expected local processes. |

## Scope

- Add `qa/process-tree-cleanup.ps1`.
- Add `qa/process-tree-cleanup.contract.ps1`.
- Wire `qa/real-backend-e2e.ps1` to the helper.
- Keep `qa/db-drill-common.ps1` compatibility wrappers while delegating to the helper.
- Add the contract to `qa/agent-harness.ps1`.
- Update related QA contracts and PDCA documents.

## Out of Scope

- Killing arbitrary local processes.
- Rewriting Playwright webServer lifecycle.
- Changing backend, frontend, or production runtime behavior.
- Committing QA runtime artifacts.

## Success Criteria

- Contract fails before the helper exists.
- Contract passes after the helper is implemented.
- Real-backend and migration drill contracts remain green.
- Agent harness exposes a narrow `process-tree-cleanup-contract` tool.
- The helper filters port cleanup to `Listen` connections and expected command-line patterns.

## Failure Criteria

- Cleanup can terminate unrelated processes on the same port without command-line validation.
- Cleanup uses broad process enumeration with no ownership or pattern guard.
- Existing migration/real-backend contracts regress.
- Verification evidence is not recorded.
