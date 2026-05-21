# T163 Remove Legacy Frontend SBOM Fallback Utility Design

Created: 2026-05-22
PDCA Phase: Design

## Design

The security gate should have one frontend SBOM source of truth:

1. `npm sbom --sbom-format cyclonedx --workspaces` writes `frontend-sbom.npm.raw.json`.
2. The gate copies the native workspace artifact to `frontend-sbom.json`.
3. `npm sbom --sbom-format cyclonedx --package-lock-only --omit=peer --omit=optional` remains secondary evidence only.

The removed fallback utility parsed `package-lock.json` directly and emitted a CycloneDX-like document outside npm's workspace model. Keeping that file available would make it easy to reintroduce weaker evidence after npm reports an invalid dependency graph.

## Contract

`qa/security-gate.contract.ps1` now checks the filesystem as well as the gate script text:

- `qa/security-frontend-sbom.mjs` must not exist.
- `qa/security-gate.ps1` must not reference the legacy utility or fallback logs.
- native workspace and package-lock-only npm SBOM artifacts must remain required.

## Risk

The full vulnerability runtime is unchanged. Local full execution remains blocked by tenant policy because npm audit and OSV require dependency package names and versions. This cleanup only removes dead fallback tooling and does not claim full vulnerability scan coverage.
