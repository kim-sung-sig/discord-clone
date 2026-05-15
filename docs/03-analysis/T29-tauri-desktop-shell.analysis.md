# T29 Tauri Desktop App Shell Analysis

작성일: 2026-05-15  
Slice: T29 Tauri Desktop App Shell

## Verification Matrix

| Gate | Command / Review | Result | Notes |
| --- | --- | --- | --- |
| Desktop contract tests | `npm run test -w apps/desktop` | PASS | 1 file, 4 tests passed. |
| Full workspace tests | `npm test --workspaces` | PASS | web 42, desktop 4, platform-shell 2, ui-contracts 4 tests passed. |
| Web production build | `npm run build -w apps/web` | PASS | Nuxt build completed with known T22 warning budget warnings. |
| Desktop packaging attempt | `npm run build -w apps/desktop` | FAIL, environment-gated | Local Tauri CLI resolved after dependency install, then failed because `cargo` is not installed. |

## Review Findings And Actions

| Finding | Severity | Action |
| --- | --- | --- |
| `frontendDist` pointed to `../../apps/web/.output/public` from `src-tauri` | P1 | Corrected to `../../web/.output/public`. |
| Desktop scripts relied on global `tauri` CLI | P1 | Added `@tauri-apps/cli` to `apps/desktop` dev dependencies and regenerated lockfile. |
| Desktop contract tests not wired to CI | P2 | Root `npm test --workspaces` now includes desktop tests locally; CI wiring remains a T31/T25 follow-up. |
| Missing `Cargo.lock` | P2 | Not generated because local Rust/Cargo is unavailable; capture as packaging prerequisite. |

## Residual Risks

- Tauri packaging is not verified in this environment because Rust/Cargo is not installed.
- `Cargo.lock` should be generated and committed once Rust toolchain is available.
- `npm install` continues to report one high-severity npm audit item; this belongs to T32 Dependency, SBOM & Vulnerability Gate.
