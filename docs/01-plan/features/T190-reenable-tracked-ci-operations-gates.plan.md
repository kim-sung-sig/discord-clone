# T190 Re-enable Tracked CI Operations Gates Plan

Created: 2026-05-21
PDCA Phase: Plan
Priority: P0
Type: CI / Security

## Problem

The CI workflow was narrowed to avoid failing GitHub Actions on `qa/*.ps1` files that existed only in the local workspace. The remaining gap is that the dependency security gate is still not wired back into CI.

## Goal

Re-enable the operations/security CI gate only when every referenced script and source dependency is included in the same task-owned commit.

## Scope

- Restore a `qa-security` GitHub Actions job.
- Ensure the job runs `qa/security-gate.contract.ps1` before `qa/security-gate.ps1`.
- Upload `qa/artifacts/security`.
- Add agent-harness tool IDs for the security gate and its contract.
- Commit the security gate scripts, allowlist, and OSV scanner dependency with the workflow wiring.

## Out of Scope

- Pushing while local or CI-risk gates are failing.
- Reintroducing untracked runtime scripts beyond the security gate dependency set.
- Fixing unrelated dirty work already present in the worktree.

## Acceptance Criteria

- RED: `qa/security-gate.contract.ps1` fails before workflow wiring because `qa-security` is absent.
- GREEN: `qa/security-gate.contract.ps1` passes.
- GREEN: staged `qa/ci-workflow.contract.ps1` passes, proving workflow `qa/*.ps1` references are tracked.
- GREEN: staged `qa/security-gate.contract.ps1` proves indirect security gate dependencies are tracked:
  - `qa/security-gate.ps1`
  - `qa/security-gate.contract.ps1`
  - `qa/security-osv-scan.mjs`
  - `qa/security-allowlist.json`
- GREEN: `qa/agent-harness.contract.ps1` passes.
- Security gate runtime is executed locally or explicitly reported as blocked with the exact failure reason.
