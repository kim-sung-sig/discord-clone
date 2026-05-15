# T22 Toolchain/Build Maintenance Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T22 Toolchain/Build Maintenance

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | Gradle/Nuxt build warnings are visible in command output but not tracked as a regression budget. |
| Solution | Inventory current warning sources, fix local actionable warnings where feasible, and add a warning budget report/harness. |
| Function UX Effect | Build/test maintainers can see warning regressions instead of relying on tribal knowledge. |
| Core Value | Toolchain upgrades become planned maintenance rather than surprise breakage. |

## Scope

- Gradle `--warning-mode all` warning inventory and cleanup where local scripts cause warnings.
- Nuxt build warning inventory including sourcemap/export warnings.
- Warning budget report in `qa/toolchain-warning-budget.md`.
- Lightweight PowerShell harness for repeatable local warning capture.

## Out of Scope

- Upgrading Gradle major version.
- Upgrading Nuxt/Vite/Vue major versions.
- CI provider-specific workflow changes unless an existing workflow is present.

## Success Criteria

- Gradle test/build warnings are either fixed or explicitly inventoried with owner/follow-up.
- Frontend build warnings are either fixed or pinned with upstream/package context.
- Warning budget is committed as a visible artifact.

## Failure Criteria

- Warnings remain only in terminal output.
- Upgrade risks are not connected to concrete files/commands.
