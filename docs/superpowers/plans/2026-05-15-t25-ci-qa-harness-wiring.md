# T25 CI QA Harness Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add GitHub Actions CI wiring for backend, frontend, real-backend QA, and toolchain warning artifacts.

**Architecture:** Keep QA logic in PowerShell scripts and make them cross-platform. The workflow composes existing scripts rather than duplicating smoke logic in YAML.

**Tech Stack:** GitHub Actions, PowerShell Core, Gradle, Node 22/npm workspaces, PostgreSQL service, Playwright Chromium.

---

### Task 1: CI Workflow Contract

**Files:**
- Create: `qa/ci-workflow.contract.ps1`
- Create later: `.github/workflows/ci.yml`

- [ ] Write a failing static contract test that checks the workflow path, required job names, PostgreSQL service, artifact upload, and QA script references.
- [ ] Run `powershell -NoProfile -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` and verify RED because workflow is missing.

### Task 2: Cross-platform QA Scripts

**Files:**
- Modify: `qa/real-backend-e2e.ps1`
- Modify: `qa/toolchain-warning-scan.ps1`
- Test: `qa/real-backend-e2e.contract.ps1`

- [ ] Select Gradle wrapper by platform.
- [ ] Select npm executable by platform.
- [ ] Apply `Start-Process -WindowStyle Hidden` only on Windows.
- [ ] Run `qa/real-backend-e2e.contract.ps1`.

### Task 3: GitHub Actions Workflow

**Files:**
- Create: `.github/workflows/ci.yml`
- Test: `qa/ci-workflow.contract.ps1`

- [ ] Add backend, frontend, qa-runtime, and qa-toolchain jobs.
- [ ] Add PostgreSQL service to qa-runtime.
- [ ] Add artifact upload steps for QA logs.
- [ ] Run `qa/ci-workflow.contract.ps1` and verify GREEN.

### Task 4: PDCA Check/Report

**Files:**
- Create: `docs/03-analysis/T25-ci-qa-harness-wiring.analysis.md`
- Create: `docs/04-report/T25-ci-qa-harness-wiring.report.md`
- Modify: `.bkit-memory.json`

- [ ] Record local verification and CI limitations.
- [ ] Commit report docs.
