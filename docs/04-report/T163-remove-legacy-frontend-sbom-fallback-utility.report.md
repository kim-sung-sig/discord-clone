# T163 Remove Legacy Frontend SBOM Fallback Utility Report

Created: 2026-05-22
Slice: T163 Remove Legacy Frontend SBOM Fallback Utility

## Completed

- Removed the legacy `qa/security-frontend-sbom.mjs` package-lock parser from the workspace.
- Strengthened `qa/security-gate.contract.ps1` to fail if the legacy fallback utility exists.
- Preserved the active frontend SBOM path: mandatory native workspace npm SBOM plus package-lock-only secondary evidence.

## Verification

- `powershell -NoProfile -ExecutionPolicy Bypass -File qa/security-gate.contract.ps1` passed with `SECURITY_GATE_CONTRACT_PASS`.
- `rg -n "security-frontend-sbom\.mjs|frontend-sbom\.fallback\.log|frontend-sbom\.npm\.error\.txt" qa/security-gate.ps1 .github package.json apps/web/package.json` returned no active references.
- `$env:SECURITY_GATE_VALIDATE_POLICY_ONLY='true'; $env:SECURITY_ARTIFACT_DIR='qa/artifacts/security-policy-only'; powershell -NoProfile -ExecutionPolicy Bypass -File qa/security-gate.ps1` passed with `SECURITY_GATE_POLICY_PASS`.

## Notes

- The full security gate remains policy-blocked locally because npm audit and OSV require external dependency metadata transmission. This task uses contract and policy-only verification only.
