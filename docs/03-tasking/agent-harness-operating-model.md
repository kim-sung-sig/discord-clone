# Agent Harness Operating Model

Purpose: make agent-driven QA execution repeatable without allowing arbitrary shell commands.

## Existing Capabilities

- PDCA documents already live under `docs/01-plan`, `docs/02-design`, `docs/03-analysis`, `docs/04-report`, and `docs/05-feedback`.
- Runtime QA scripts already exist for API smoke, real-backend e2e, CI workflow contract, migration guard, and toolchain warning scan.
- Agent team roles already live in `docs/03-tasking/agent-team-operating-model.md`.

## Missing Capabilities Filled By This Model

- A single Tool ID Allowlist runner for agents.
- A repeatable Ticket Creation command for PDCA document sets.
- A Loop State contract that records the last tool result and next action.

## Tool ID Allowlist

Agents run `qa/agent-harness.ps1 -Tool <id>` instead of constructing arbitrary commands.

Allowed tool ids:

| Tool ID | Purpose |
| --- | --- |
| `backend-test` | Run backend Gradle tests. |
| `backend-boot-test` | Run backend boot module tests. |
| `web-test` | Run npm workspace tests. |
| `web-build` | Build the web workspace. |
| `web-e2e` | Run web e2e tests. |
| `openapi-check` | Check generated API contract drift. |
| `docker-config` | Validate Docker Compose config without starting containers. |
| `api-smoke` | Run API smoke against a running backend. |
| `real-backend-e2e-contract` | Validate real-backend e2e harness shape. |
| `real-backend-e2e` | Run real backend smoke and Playwright flow. |
| `ci-workflow-contract` | Validate CI workflow structure. |
| `toolchain-warning-scan` | Run warning budget scan. |
| `migration-guard-contract` | Validate migration guard behavior. |
| `backend-style-contract` | Validate backend style, DDD, DTO, method signature, and layer boundary rules. |
| `frontend-style-contract` | Validate frontend style, token storage, exported function signature, and platform boundary rules. |
| `development-process-contract` | Validate TDD/DDD/process rules are documented for agents. |
| `style-architecture-governance-contract` | Validate Mermaid architecture/style guide and harness wiring. |
| `review-context-isolation-contract` | Validate diff-only review context separation and commit/push policy. |
| `task-complete-contract` | Validate task completion commit/push helper shape. |
| `review-packet-contract` | Validate diff-only review packet generator shape. |
| `real-lint-contract` | Validate real backend/frontend lint tool wiring. |
| `frontend-lint` | Run ESLint against frontend and shared TypeScript. |
| `backend-lint` | Run Gradle Checkstyle against backend Java sources. |
| `format-check` | Run Prettier format checks for lint configuration files. |

## Ticket Creation

Use `qa/new-ticket.ps1` to create a complete PDCA document set:

```powershell
pwsh qa/new-ticket.ps1 -Id T120 -Title "Agent Harness PDCA Loop" -Type qa-infra -Priority P0
```

The script creates Plan, Design, Analysis, Report, and Feedback files and appends the task to `docs/03-tasking/improvement-task-backlog.md` when it is not already listed.

## PDCA Loop

1. Plan: create or read the ticket docs and define success criteria.
2. Do: implement within the allowed write paths.
3. Review: close implementation context and create a Diff-Only Review Packet.
4. Check: run allowlisted harness tools and record evidence in Analysis.
5. Act: capture failures or follow-ups in Feedback and update the Report when complete.
6. Commit And Push Gate: commit only the task-owned paths and push after the commit when remote is configured.

## Loop State

`docs/03-tasking/agent-loop-state.md` defines the state shape. `qa/agent-harness.ps1` writes the latest local state to `qa/artifacts/agent-harness/agent-harness-state.json`.

## Safety Rules

- Do not add broad arbitrary command execution to the harness.
- Do not add generic argument passthrough to the harness.
- Do not add destructive tools without an explicit contract test and human approval.
- Keep runtime logs under `qa/artifacts/`.
- Prefer adding narrow Tool IDs over exposing shell passthrough.
- Review agents must be started from a fresh context with only Plan/Design, `git diff --stat`, `git diff -- <task-owned paths>`, relevant tests, artifact paths, and known residual risks.
- Task completion must not commit unrelated dirty work.

## Style And Architecture Governance

Use `docs/03-tasking/style-architecture-governance.md` as the agent-readable guide for:

- TDD Evidence: RED/GREEN/REFACTOR proof in Analysis or Report.
- Ubiquitous Language: domain names should reflect Discord domain concepts.
- DTO Boundary: transport DTOs stay at boot/controller or frontend service boundaries.
- Method Signature: prefer command/query/options objects when signatures grow.

## Review Packet And Task Completion Helpers

Use `qa/new-review-packet.ps1` to generate a diff-only review packet before starting a fresh review agent. Use `qa/task-complete.ps1` after all gates pass to commit only task-owned paths and push the current branch. Both helpers are contract-tested and exposed through the agent harness.
