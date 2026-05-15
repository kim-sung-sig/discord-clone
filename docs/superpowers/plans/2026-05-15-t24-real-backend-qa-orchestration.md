# T24 Real Backend QA Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a repeatable local QA harness that runs API smoke and real-backend Playwright against a live Spring backend with PostgreSQL env overrides.

**Architecture:** Add one PowerShell orchestration script under `qa/` that composes existing backend bootRun, `qa/api-smoke.ps1`, and `apps/web/tests/e2e/real-backend.spec.ts`. Add one PowerShell contract test that validates the harness shape without starting external services.

**Tech Stack:** PowerShell, Gradle Spring Boot `:backend:boot:bootRun`, Nuxt Playwright, existing `qa/api-smoke.ps1`.

---

### Task 1: Harness Contract Test

**Files:**
- Create: `qa/real-backend-e2e.contract.ps1`
- Create later: `qa/real-backend-e2e.ps1`

- [ ] **Step 1: Write the failing contract test**

Create `qa/real-backend-e2e.contract.ps1` with assertions that fail until the harness exists and contains the required parameters and command wiring.

- [ ] **Step 2: Run RED**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.contract.ps1`

Expected: FAIL because `qa/real-backend-e2e.ps1` is missing.

- [ ] **Step 3: Commit test with implementation after GREEN**

Commit after Task 2 passes.

### Task 2: Real Backend QA Harness

**Files:**
- Create: `qa/real-backend-e2e.ps1`
- Test: `qa/real-backend-e2e.contract.ps1`

- [ ] **Step 1: Implement parameters**

Required defaults:

```powershell
[string] $BackendUrl = 'http://127.0.0.1:8080'
[string] $PostgresJdbcUrl = 'jdbc:postgresql://127.0.0.1:5432/discord'
[string] $PostgresUser = 'dev_user'
[string] $PostgresPassword = 'dev_password'
[string] $ArtifactsDir = 'qa/artifacts/real-backend-e2e'
[int] $BackendStartupTimeoutSeconds = 120
[switch] $SkipServiceStart
```

- [ ] **Step 2: Implement backend health/start/stop**

Use `/actuator/health`, start only when needed, and stop only the process started by this script.

- [ ] **Step 3: Implement command execution**

Run `qa/api-smoke.ps1 -BaseUrl $BackendUrl`, then run `npm run e2e -- tests/e2e/real-backend.spec.ts` in `apps/web` with `REAL_BACKEND_E2E=1`, `REAL_BACKEND_BASE_URL=$BackendUrl`, and `NUXT_PUBLIC_API_BASE_URL=$BackendUrl`.

- [ ] **Step 4: Run GREEN contract test**

Run: `powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.contract.ps1`

Expected: PASS.

- [ ] **Step 5: Commit harness**

Commit: `test: add real backend qa orchestration harness`.

### Task 3: PDCA Check/Report

**Files:**
- Create: `docs/03-analysis/T24-real-backend-qa-orchestration.analysis.md`
- Create: `docs/04-report/T24-real-backend-qa-orchestration.report.md`
- Modify: `.bkit-memory.json`

- [ ] **Step 1: Record verification evidence**

Record contract test result and any runtime execution result if services are available.

- [ ] **Step 2: Record residual risks**

If runtime execution is not run, explicitly document that PostgreSQL/backend/browser dependencies are required.

- [ ] **Step 3: Commit PDCA report**

Commit: `docs: record T24 real backend qa PDCA`.
