# T163 Remove Legacy Frontend SBOM Fallback Utility Plan

Created: 2026-05-22
PDCA Phase: Plan
Priority: P3
Type: QA / Security

## Problem

T162 made native npm workspace SBOM generation mandatory and removed the security gate execution path that called `qa/security-frontend-sbom.mjs`. The old fallback utility still exists in the workspace, which leaves an unused security artifact generator available for accidental reuse.

## Goal

Remove the legacy frontend SBOM fallback utility and make the security gate contract fail if it is reintroduced.

## Scope

- Delete `qa/security-frontend-sbom.mjs`.
- Strengthen `qa/security-gate.contract.ps1` to assert that the legacy fallback utility is absent.
- Keep the active security gate on native npm workspace SBOM output plus package-lock-only secondary evidence.
- Document the cleanup and verification evidence.

## Out of Scope

- Changing OSV or npm audit behavior.
- Running the full local vulnerability gate while tenant policy blocks dependency metadata transmission.
- Refactoring unrelated security documentation.

## Acceptance Criteria

- RED: `qa/security-frontend-sbom.mjs` exists before the cleanup.
- GREEN: `qa/security-gate.contract.ps1` passes and proves the utility is absent.
- GREEN: no active QA or CI path references `qa/security-frontend-sbom.mjs`.
- GREEN: policy-only `qa/security-gate.ps1` passes without external dependency queries.
