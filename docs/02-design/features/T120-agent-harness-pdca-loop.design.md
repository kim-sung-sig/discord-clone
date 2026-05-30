# T120 Agent Harness PDCA Loop Design

Created: 2026-05-19
PDCA Phase: Design
Slice: T120 Agent Harness PDCA Loop

## Architecture Decision

Extend the existing PowerShell QA style instead of adding a new runtime. `qa/agent-harness.ps1` is a small allowlist dispatcher over existing scripts and standard build commands. `qa/new-ticket.ps1` generates the existing PDCA document layout and appends a backlog row. Contract tests keep the shape stable without starting services.

## Allowed Write Paths

- `qa/agent-harness.ps1`
- `qa/agent-harness.contract.ps1`
- `qa/new-ticket.ps1`
- `qa/new-ticket.contract.ps1`
- `docs/03-tasking/agent-harness-operating-model.md`
- `docs/03-tasking/agent-loop-state.md`
- `docs/01-plan/features/T120-agent-harness-pdca-loop.plan.md`
- `docs/02-design/features/T120-agent-harness-pdca-loop.design.md`
- `docs/03-analysis/T120-agent-harness-pdca-loop.analysis.md`
- `docs/04-report/T120-agent-harness-pdca-loop.report.md`
- `docs/05-feedback/T120-agent-harness-pdca-loop.feedback.md`

## Forbidden Changes

- Do not modify backend or frontend product behavior.
- Do not introduce arbitrary command execution or generic argument passthrough.
- Do not commit runtime artifacts or secrets.

## Agent Packet

~~~text
Task ID: T120
Goal: Agent Harness PDCA Loop
Required docs:
- docs/01-plan/features/T120-agent-harness-pdca-loop.plan.md
- docs/02-design/features/T120-agent-harness-pdca-loop.design.md
Allowed write paths:
- qa/agent-harness.ps1
- qa/agent-harness.contract.ps1
- qa/new-ticket.ps1
- qa/new-ticket.contract.ps1
- docs/03-tasking/**
- docs/01-plan/features/T120-agent-harness-pdca-loop.plan.md
- docs/02-design/features/T120-agent-harness-pdca-loop.design.md
- docs/03-analysis/T120-agent-harness-pdca-loop.analysis.md
- docs/04-report/T120-agent-harness-pdca-loop.report.md
- docs/05-feedback/T120-agent-harness-pdca-loop.feedback.md
Read-only context paths:
- docs/03-tasking/agent-team-operating-model.md
- qa/harness/README.md
Forbidden changes:
- No arbitrary command execution
- No unrelated product changes
Expected tests:
- pwsh qa/agent-harness.contract.ps1
- pwsh qa/new-ticket.contract.ps1
- pwsh qa/agent-harness.ps1 -Tool ci-workflow-contract
Expected artifacts:
- docs/03-analysis/T120-agent-harness-pdca-loop.analysis.md
- docs/04-report/T120-agent-harness-pdca-loop.report.md
Return format:
- status
- changed files
- tests run and result
- residual risks
~~~

## Verification Commands

~~~powershell
pwsh qa/agent-harness.contract.ps1
pwsh qa/new-ticket.contract.ps1
pwsh qa/agent-harness.ps1 -Tool ci-workflow-contract
~~~

## Risks

- Some allowlisted tools still require local services, Docker, or browser dependencies.
- The harness state artifact is local runtime evidence and must stay out of git.
