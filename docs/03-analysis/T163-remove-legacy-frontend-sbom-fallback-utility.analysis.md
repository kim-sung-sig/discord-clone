# T163 Remove Legacy Frontend SBOM Fallback Utility Analysis

Created: 2026-05-22
PDCA Phase: Check

## TDD Evidence

| Phase | Command | Result | Evidence |
| --- | --- | --- | --- |
| RED | `Test-Path qa/security-frontend-sbom.mjs` | FAIL state confirmed | The legacy fallback utility existed before cleanup. |
| Reference scan | `rg -n "security-frontend-sbom|frontend-sbom|fallback|npm sbom|SBOM" qa .github docs package.json apps/web/package.json` | PASS | Active QA references only native npm SBOM artifacts; historical docs mention the removed fallback. |
| GREEN | `powershell -NoProfile -ExecutionPolicy Bypass -File qa/security-gate.contract.ps1` | PASS | `SECURITY_GATE_CONTRACT_PASS` |
| Active reference scan | `rg -n "security-frontend-sbom\.mjs|frontend-sbom\.fallback\.log|frontend-sbom\.npm\.error\.txt" qa/security-gate.ps1 .github package.json apps/web/package.json` | PASS | No active gate, workflow, or package-script references remain. |
| Policy-only security gate | `$env:SECURITY_GATE_VALIDATE_POLICY_ONLY='true'; $env:SECURITY_ARTIFACT_DIR='qa/artifacts/security-policy-only'; powershell -NoProfile -ExecutionPolicy Bypass -File qa/security-gate.ps1` | PASS | `SECURITY_GATE_POLICY_PASS` |

## Findings

- `git ls-files -- qa/security-frontend-sbom.mjs` returned no tracked path, so the file was a leftover workspace utility rather than part of the committed T190 security gate.
- `qa/security-gate.ps1` already fails closed on native workspace SBOM failure and does not invoke the fallback utility.
- `qa/security-gate.contract.ps1` already rejected fallback text references; T163 extends that protection to the actual file path so a local leftover cannot become the next source of drift.
- The full local vulnerability gate was intentionally not run because tenant policy still blocks npm audit and OSV dependency metadata transmission.
