# T162 Strict Workspace Native NPM SBOM Cleanup Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T162 Strict Workspace Native NPM SBOM Cleanup

## Findings

| Finding | Result |
| --- | --- |
| `npm sbom --workspaces` failed with `ESBOMPROBLEMS` | Root `commander@10.0.1` did not satisfy `@bomb.sh/tab` optional peer `^13.1.0`. |
| Package override did not fix the tree | `commander` is a peer, not an ordinary dependency, so the override did not create a valid peer installation. |
| Workspace-local `commander@13.1.0` did not fix the peer | The peer consumer is nested under `@nuxt/cli`, so the workspace-local dependency was not an ancestor. |
| Root `commander@13.1.0` fixed the peer | npm nested `commander@10.0.1` under `editorconfig` and let `@bomb.sh/tab` use root `commander@13.1.0`. |
| Security gate still had a fallback path | Contract now rejects fallback references, and the gate throws on native workspace SBOM failure. |

## Security Review

The gate now treats native workspace SBOM generation as mandatory supply-chain evidence. This avoids silently accepting a fallback artifact after npm reports an invalid dependency tree.

## Residual Risk

- `qa/security-frontend-sbom.mjs` remains in the repository as an unused legacy utility. It can be removed in a later cleanup if no historical workflow depends on it.
