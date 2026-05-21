# T190 Re-enable Tracked CI Operations Gates Analysis

Created: 2026-05-21
PDCA Phase: Check

## TDD Evidence

| Phase | Command | Result | Evidence |
| --- | --- | --- | --- |
| RED | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/security-gate.contract.ps1` | FAIL | `CI workflow must include qa-security job` |
| GREEN | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/security-gate.contract.ps1` | PASS | `SECURITY_GATE_CONTRACT_PASS` |
| CI tracking guard | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` | PASS | `CI_WORKFLOW_CONTRACT_PASS` |
| Harness regression | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.contract.ps1` | PASS | `AGENT_HARNESS_CONTRACT_PASS` |
| Harness tool | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.ps1 -Tool security-gate-contract` | PASS | `AGENT_HARNESS_TOOL_PASS security-gate-contract` |
| Policy-only security gate | `$env:SECURITY_GATE_VALIDATE_POLICY_ONLY='true'; powershell -NoProfile -ExecutionPolicy Bypass -File qa/security-gate.ps1` | PASS | `SECURITY_GATE_POLICY_PASS` |
| Full security gate | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/security-gate.ps1` | WAIVED | User approval was granted on 2026-05-22, but tenant policy rejected execution because the full gate sends dependency package names/versions and SBOM-derived package data to npm audit and OSV. Temporary waiver approved for local commit; do not claim full vulnerability runtime coverage from this commit. |

## Current Findings

- `qa/security-gate.ps1`, `qa/security-gate.contract.ps1`, `qa/security-osv-scan.mjs`, and `qa/security-allowlist.json` are included in the task-owned commit set.
- Re-enabling the workflow without staging those files would fail the tracked-script CI guard.
- The final `qa/ci-workflow.contract.ps1` check must be run after staging because it uses `git ls-files`.
- The first full local security-gate attempt also exposed non-JSON warning text before the npm audit JSON payload when the audit endpoint was unavailable. `qa/security-gate.ps1` now parses the JSON object portion of the artifact and fails with `npm audit endpoint failed` when the JSON payload contains an audit endpoint error.
- The full vulnerability runtime requires external advisory-service matching. That matching cannot happen without sending the dependency identity/version set to npm audit and OSV, so the local full-gate run remains waived under current tenant policy.

## Subagent Review

| Agent | Result | Follow-up |
| --- | --- | --- |
| Code Quality/Security Agent | CHANGES_REQUESTED | Fixed security gate dependency staging, CVSS base-score severity mapping, unknown severity fail-closed behavior, allowlist severity matching, artifact path deletion guard, OSV fetch timeout, CI job timeout, and job permissions. |
| QA/Spec Agent | CHANGES_REQUESTED | Added tracked dependency assertions to `qa/security-gate.contract.ps1` for indirect security gate dependencies and updated acceptance criteria. |
