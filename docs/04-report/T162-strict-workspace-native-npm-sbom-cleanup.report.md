# T162 Strict Workspace Native NPM SBOM Cleanup Report

Date: 2026-05-20
Slice: T162 Strict Workspace Native NPM SBOM Cleanup

## Completed

- Added root `commander@^13.1.0` so npm's workspace tree satisfies `@bomb.sh/tab` optional peer requirements.
- Updated `qa/security-gate.ps1` to fail immediately when native workspace SBOM generation fails.
- Removed fallback SBOM invocation from the security gate execution path.
- Strengthened `qa/security-gate.contract.ps1` to reject fallback references and require the native workspace SBOM artifact.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/security-gate.contract.ps1` failed because the gate still referenced `qa/security-frontend-sbom.mjs`.
- GREEN after implementation:
  - `npm ls '@bomb.sh/tab' commander --all` passed with `@bomb.sh/tab` using `commander@13.1.0`.
  - `npm sbom --sbom-format cyclonedx --workspaces` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/security-gate.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/security-gate.ps1` passed.

## Notes

- The package-lock-only SBOM remains as secondary evidence with peer and optional dependency noise omitted.
