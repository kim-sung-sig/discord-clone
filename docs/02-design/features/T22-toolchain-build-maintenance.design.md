# T22 Toolchain/Build Maintenance Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T22 Toolchain/Build Maintenance

## Architecture Decision

Treat toolchain warnings as managed QA artifacts. T22 does not force risky dependency upgrades; it separates actionable local build script warnings from upstream package warnings and records the current budget.

## Harness

Create `qa/toolchain-warning-scan.ps1`:

- runs Gradle with `--warning-mode all`
- runs frontend `npm run build`
- writes logs under `qa/artifacts/toolchain/`
- exits non-zero only on build/test failure, not on known warnings

## Report

Create `qa/toolchain-warning-budget.md` with:

- command
- current warning summary
- owner/follow-up
- acceptable budget for next run

## Test Strategy

- Run `./gradlew.bat test --warning-mode all`.
- Run `npm run build` in `apps/web`.
- Run the harness once if command environment supports it.

## Risks

- Some warnings may originate from Gradle plugins or Nuxt/Vite dependencies, not local code.
- npm/node warning text can change by package patch version.
