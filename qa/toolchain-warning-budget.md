# Toolchain Warning Budget

작성일: 2026-05-15  
Slice: T22 Toolchain/Build Maintenance

## Commands

| Command | Result | Warning Budget |
| --- | --- | --- |
| `./gradlew.bat test --warning-mode all` | PASS | 0 Gradle deprecation warnings |
| `npm run build` in `apps/web` | PASS | 2 known frontend warnings |

## Current Warning Inventory

| Area | Warning | Source | Status | Follow-up |
| --- | --- | --- | --- | --- |
| Gradle | none observed under `--warning-mode all` | local Gradle build | Clean | Keep budget at 0 |
| Nuxt/Vite | `[plugin nuxt:module-preload-polyfill] Sourcemap is likely to be incorrect` | Nuxt module preload polyfill transform | Known upstream/tooling warning | Track during Nuxt/Vite upgrades; no local transform code to fix |
| Node/Vue package exports | `[DEP0155] Use of deprecated trailing slash pattern mapping "./" in @vue/shared package exports` | `@vue/shared` consumed through Nitro error template | Known upstream package export warning | Re-check after Vue/Nuxt dependency update; avoid local workaround |

## Regression Policy

- Gradle deprecation warning budget: `0`.
- Frontend known warning budget: `2` until Nuxt/Vue upstream packages remove them.
- Any new warning must be added here with source, owner, and follow-up or fixed in the same task.

## Harness

Run:

```powershell
.\qa\toolchain-warning-scan.ps1
```

The harness writes logs to `qa/artifacts/toolchain/` and fails only when Gradle or Nuxt build fails.
