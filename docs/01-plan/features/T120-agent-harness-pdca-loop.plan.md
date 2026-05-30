# T120 Agent Harness PDCA Loop Plan

Created: 2026-05-19
PDCA Phase: Plan
Slice: T120 Agent Harness PDCA Loop
Type: qa-infra
Priority: P0

## Executive Summary

| View | Content |
| --- | --- |
| Problem | The repo has many QA scripts and PDCA docs, but no single safe Tool ID runner or ticket generator for agents. |
| Solution | Add allowlisted harness execution, PDCA ticket creation, contract tests, and loop state documentation. |
| Function UX Effect | Agents can issue bounded tickets, run approved tools, and record loop state without inventing shell commands. |
| Core Value | Repeatable agent work with safer command boundaries and consistent PDCA evidence. |

## Scope

- Add `qa/agent-harness.ps1` and `qa/agent-harness.contract.ps1`.
- Add `qa/new-ticket.ps1` and `qa/new-ticket.contract.ps1`.
- Add `docs/03-tasking/agent-harness-operating-model.md`.
- Add `docs/03-tasking/agent-loop-state.md`.
- Generate this T120 PDCA document set with the new ticket script.
- Update `docs/03-tasking/improvement-task-backlog.md`.

## Out of Scope

- Changing existing backend/frontend product behavior.
- Starting Docker containers or long-running services.
- Adding arbitrary shell passthrough to the harness.

## Success Criteria

- Agent harness exposes only named Tool IDs.
- Ticket generator creates Plan, Design, Analysis, Report, and Feedback files.
- Contract tests validate both scripts and loop docs.
- A real allowlisted tool run updates the local harness state artifact.

## Failure Criteria

- Agents can execute arbitrary commands through the harness.
- Ticket generation skips any PDCA phase document.
- Harness execution does not leave evidence or state.
