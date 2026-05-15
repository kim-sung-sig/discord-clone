# T27 Multi-Platform Frontend Architecture Analysis

작성일: 2026-05-15  
Slice: T27 Multi-Platform Frontend Architecture & Screen Contracts

## Verification Matrix

| Gate | Command / Review | Result | Notes |
| --- | --- | --- | --- |
| Workspace tests | `npm test --workspaces` | PASS | `apps/web` 41 tests, `platform-shell` 2 tests, `ui-contracts` 4 tests passed. |
| Root E2E script | `$env:NUXT_DEV_PORT='3012'; npm run e2e` | PASS | Root script now runs browser E2E only for `apps/web`; 15 passed, 1 real-backend test skipped. |
| Web build | `npm run build -w apps/web` | PASS | Nuxt production build completed with known T22 warning budget warnings. |
| Whitespace | `git diff --check` | PASS | CRLF warnings only. |
| Spec review | Spec Compliance Agent | PASS after fix | `package-lock.json` was added to allowed task paths because workspace membership changed. |
| Quality review | Code Quality Agent | PASS after fix | Removed public adapter implementation names and added auth/permission navigation guards. |

## Findings And Actions

| Finding | Cause | Action |
| --- | --- | --- |
| `package-lock.json` outside initial allowed paths | npm workspace membership changed after adding `packages/*` | Updated implementation plan to allow lockfile updates for workspace changes and regenerated lockfile. |
| Root `e2e` failed on new packages | `npm run e2e --workspaces --if-present` still reported missing scripts for package workspaces | Root `e2e` now targets `apps/web`, the only workspace with browser E2E. |
| Public capability exposed adapter names | `PlatformCapability` included `adapter: 'browser' | 'tauri' | 'native'` | Removed adapter from public contract so consumers cannot branch on implementation runtime. |
| Desktop deep link had no guard contract | `deep-link-or-last-channel` lacked explicit auth/permission guard fields | Added `requiresAuth`, `requiresPermissionCheck`, and `fallbackOnDenied` to navigation contract and tests. |
| Permission coverage could drift | permission contracts were array-only | Added `permissionContractMap satisfies Record<PermissionAction, PermissionContract>` and uniqueness/coverage test. |
| E2E first run rendered Tripler page | Playwright reused existing port 3000 server | Re-ran with `NUXT_DEV_PORT=3012`; port-collision risk remains a T28/T25 harness consideration. |

## Residual Risks

- Package exports are source-only `.ts` exports. This is acceptable for current Vite/Vitest/Nuxt workspace usage, but T42 should revisit generated package artifacts if external consumers appear.
- `npm install --package-lock-only --ignore-scripts` reports one high-severity npm audit item. This is outside T27 scope and belongs to T32 Dependency, SBOM & Vulnerability Gate.
- Playwright defaults can still reuse a wrong local server on port 3000 if a developer runs E2E without port override. T28 should prefer explicit mobile/PWA port handling in QA instructions.
