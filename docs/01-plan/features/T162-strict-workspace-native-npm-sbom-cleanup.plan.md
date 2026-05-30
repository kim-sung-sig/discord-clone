# T162 Strict Workspace Native NPM SBOM Cleanup Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T162 Strict Workspace Native NPM SBOM Cleanup

## Executive Summary

| View | Content |
| --- | --- |
| Problem | `npm sbom --workspaces` failed because `@bomb.sh/tab` resolved its optional `commander@^13.1.0` peer to the root `commander@10.0.1`. |
| Solution | Pin `commander@^13.1.0` at the root and remove the legacy fallback path from the security gate. |
| Operator Effect | The security gate now fails closed if native workspace SBOM generation fails. |
| Core Value | Frontend SBOM evidence comes from npm's native workspace inventory instead of an emergency fallback generator. |

## Scope

- Fix npm dependency layout so strict workspace SBOM generation passes.
- Update the security gate contract to reject fallback SBOM generation.
- Update the security gate to require `npm sbom --workspaces` success.
- Keep the package-lock-only SBOM artifact as secondary evidence.

## Out of Scope

- Removing the legacy fallback script file from the repository.
- Changing OSV vulnerability scan behavior.
- Reworking backend Gradle dependency inventory.

## Success Criteria

- Contract fails while the security gate still references fallback generation.
- `npm sbom --sbom-format cyclonedx --workspaces` passes.
- `qa/security-gate.contract.ps1` passes.
- `qa/security-gate.ps1` passes.
