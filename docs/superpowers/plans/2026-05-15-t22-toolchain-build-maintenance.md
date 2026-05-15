# T22 Toolchain/Build Maintenance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make current Gradle/Nuxt warning state visible and repeatable.

**Architecture:** A committed warning budget report plus local scan harness captures warning regressions without forcing risky major dependency upgrades.

**Tech Stack:** Gradle 8, Spring Boot, Nuxt, npm, PowerShell.

---

### Task 1: Planning
- [ ] Commit T22 plan/design docs.

### Task 2: Warning Inventory
- [ ] Run `./gradlew.bat test --warning-mode all`.
- [ ] Run `npm run build` in `apps/web`.
- [ ] Record warnings in `qa/toolchain-warning-budget.md`.

### Task 3: Harness
- [ ] Add `qa/toolchain-warning-scan.ps1`.
- [ ] Run the harness.
- [ ] Commit warning budget and harness.

### Task 4: Report
- [ ] Create T22 analysis/report docs.
- [ ] Run final verification.
- [ ] Commit report.
