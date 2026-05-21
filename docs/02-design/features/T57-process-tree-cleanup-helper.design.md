# T57 Process Tree Cleanup Helper Design

Created: 2026-05-21
PDCA Phase: Design
Slice: T57 Process Tree Cleanup Helper

## Architecture Decision

Create `qa/process-tree-cleanup.ps1` as a small shared PowerShell helper with project-specific function names:

- `Test-QaIsWindows`
- `Write-QaCleanupStep`
- `Stop-QaProcessTree`
- `Stop-QaListeningProcessByPort`

The helper keeps destructive scope narrow:

- Process-tree cleanup requires an explicit PID supplied by the harness.
- Port cleanup only runs on Windows, only inspects `Listen` connections, ignores PID `0`, and requires the process command line to match an expected pattern.
- Harnesses keep their own lifecycle decisions; the helper only performs bounded cleanup.

`qa/db-drill-common.ps1` keeps legacy wrappers (`Stop-ProcessTree`, `Stop-BackendPortProcess`) so existing migration drill code and contracts remain stable while the implementation is centralized.

## Allowed Write Paths

- `qa/process-tree-cleanup.ps1`
- `qa/process-tree-cleanup.contract.ps1`
- `qa/real-backend-e2e.ps1`
- `qa/real-backend-e2e.contract.ps1`
- `qa/db-drill-common.ps1`
- `qa/migration-drill.contract.ps1`
- `qa/agent-harness.ps1`
- `qa/agent-harness.contract.ps1`
- `docs/01-plan/features/T57-process-tree-cleanup-helper.plan.md`
- `docs/02-design/features/T57-process-tree-cleanup-helper.design.md`
- `docs/03-analysis/T57-process-tree-cleanup-helper.analysis.md`
- `docs/04-report/T57-process-tree-cleanup-helper.report.md`
- `docs/05-feedback/T57-process-tree-cleanup-helper.feedback.md`
- `docs/03-tasking/improvement-task-backlog.md`

## Forbidden Changes

- No product runtime behavior changes.
- No global process cleanup by process name alone.
- No deletion of runtime artifacts from arbitrary directories.
- No push while CI-failure risk remains unresolved.

## Agent Packet

~~~text
Task ID: T57
Goal: Add a bounded QA process-tree cleanup helper and wire it into existing QA harnesses.
Required docs:
- docs/01-plan/features/T57-process-tree-cleanup-helper.plan.md
- docs/02-design/features/T57-process-tree-cleanup-helper.design.md
Allowed write paths:
- qa/process-tree-cleanup.ps1
- qa/process-tree-cleanup.contract.ps1
- qa/real-backend-e2e.ps1
- qa/real-backend-e2e.contract.ps1
- qa/db-drill-common.ps1
- qa/migration-drill.contract.ps1
- qa/agent-harness.ps1
- qa/agent-harness.contract.ps1
- docs/**/T57-process-tree-cleanup-helper.*
- docs/03-tasking/improvement-task-backlog.md
Read-only context paths:
- qa/real-backend-e2e.ps1
- qa/migration-drill.ps1
- qa/db-drill-common.ps1
- C:/tmp/ObsidianVaults/discord-llm-wiki/wiki/QA Infra Operations.md
Forbidden changes:
- Do not kill arbitrary processes.
- Do not change backend/frontend product behavior.
Expected tests:
- powershell -NoProfile -ExecutionPolicy Bypass -File qa/process-tree-cleanup.contract.ps1
- powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.contract.ps1
- powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-drill.contract.ps1
- powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.contract.ps1
- powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.ps1 -Tool process-tree-cleanup-contract
Expected artifacts:
- No committed runtime artifacts.
Return format:
- status
- changed files
- tests run and result
- residual risks
~~~

## Verification Commands

~~~powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa/process-tree-cleanup.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/migration-drill.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.contract.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.ps1 -Tool process-tree-cleanup-contract
~~~

## Risks

- Static contracts prove wiring and safety guard shape, not every OS-specific process edge case.
- Existing Playwright webServer shutdown behavior can still hang independently of backend cleanup; this task reduces backend process residue, not Playwright internals.
