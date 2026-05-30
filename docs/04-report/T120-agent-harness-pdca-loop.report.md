# T120 Agent Harness PDCA Loop Report

Created: 2026-05-19
PDCA Phase: Report
Slice: T120 Agent Harness PDCA Loop

## Summary

T120 adds the missing agent automation layer around existing QA and PDCA assets. The repo already had individual QA scripts and tasking docs; this adds a safe Tool ID runner, ticket generator, loop state contract, and contract tests.

## Delivered

- Added `qa/agent-harness.ps1` allowlist runner.
- Added `qa/new-ticket.ps1` PDCA ticket generator.
- Added contract tests for both scripts.
- Added agent harness operating model and loop state docs.
- Generated the T120 PDCA document set and backlog entry.

## Test Evidence

- `qa/agent-harness.contract.ps1`: PASS
- `qa/new-ticket.contract.ps1`: PASS
- `qa/agent-harness.ps1 -List`: PASS
- `qa/new-ticket.ps1 -Id T120 -Title "Agent Harness PDCA Loop" -Type qa-infra -Priority P0`: PASS
- `qa/agent-harness.ps1 -Tool ci-workflow-contract`: PASS

## Residual Risks

- Runtime-heavy Tool IDs still require their normal local prerequisites.
- Tool parameters are intentionally not generic; add a new narrow Tool ID when a task needs a variant.
- `qa/artifacts/agent-harness/agent-harness-state.json` is local evidence and should not be committed.
