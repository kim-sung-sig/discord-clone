# T17 Observability/Structured Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add request-correlated structured logs and metrics for API requests, auth failures, and forbidden/unauthorized outcomes.

**Architecture:** Extend `OperationalHardeningFilter` because it already owns `/api/**` request id generation and response echo. Add Logback JSON console output and Micrometer timer/counter recording with normalized paths to avoid high-cardinality labels.

**Tech Stack:** Spring Boot 3.3, SLF4J MDC, Logback, Micrometer/Actuator, MockMvc, AssertJ.

---

### Task 1: PDCA Planning Documents

**Files:**
- Create: `docs/01-plan/features/T17-observability-structured-logging.plan.md`
- Create: `docs/02-design/features/T17-observability-structured-logging.design.md`
- Create: `docs/superpowers/plans/2026-05-14-t17-observability-structured-logging.md`
- Modify: `.bkit-memory.json`

- [ ] **Step 1: Commit planning artifacts**

Run:

```powershell
git add .bkit-memory.json docs/01-plan/features/T17-observability-structured-logging.plan.md docs/02-design/features/T17-observability-structured-logging.design.md docs/superpowers/plans/2026-05-14-t17-observability-structured-logging.md
git commit -m "docs: plan T17 observability structured logging"
```

Expected: commit succeeds.

### Task 2: MDC And Metrics Tests

**Files:**
- Modify: `backend/boot/src/test/java/com/example/discord/ops/OperationalHardeningFilterTest.java`

- [ ] **Step 1: Add failing tests**

Add tests that:

- call a test-only `/api/observability/mdc` endpoint with `X-Request-Id: t17-request-123`;
- assert the response body contains `request_id`, `http_method`, and normalized `http_path`;
- assert `MDC.get("request_id")` is null after MockMvc returns;
- call `/api/auth/login` with invalid credentials and assert `discord.auth.failures` increments.

- [ ] **Step 2: Run red test**

Run:

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks
```

Expected before implementation: fail because MDC and metrics are not populated yet.

### Task 3: Filter Observability Implementation

**Files:**
- Modify: `backend/boot/src/main/java/com/example/discord/ops/OperationalHardeningFilter.java`

- [ ] **Step 1: Implement MDC and metrics**

Add constructor injection of `MeterRegistry`, populate MDC before dispatch, record a request timer in `finally`, count 401/403/423 rejections, count `/api/auth/login` failures for 401/423, and clear MDC keys in `finally`.

- [ ] **Step 2: Run green test**

Run:

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit backend observability**

Run:

```powershell
git add backend/boot/src/main/java/com/example/discord/ops/OperationalHardeningFilter.java backend/boot/src/test/java/com/example/discord/ops/OperationalHardeningFilterTest.java
git commit -m "feat: add api observability context"
```

Expected: commit succeeds.

### Task 4: JSON Log Baseline And Runtime Smoke

**Files:**
- Create: `backend/boot/src/main/resources/logback-spring.xml`
- Create: `qa/observability-smoke.ps1`

- [ ] **Step 1: Add Logback JSON console pattern**

Create a JSON console appender that includes timestamp, level, service, thread, logger, `request_id`, `http_method`, `http_path`, `http_status`, and message. Do not include headers or bodies.

- [ ] **Step 2: Add runtime smoke**

Create `qa/observability-smoke.ps1` that calls `/api/premium/catalog` with a caller-provided `X-Request-Id`, asserts the response echoes it, then scans a provided log file for the same JSON `request_id`.

- [ ] **Step 3: Verify**

Run:

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks
.\gradlew.bat test
```

Expected: both commands succeed.

- [ ] **Step 4: Commit log baseline**

Run:

```powershell
git add backend/boot/src/main/resources/logback-spring.xml qa/observability-smoke.ps1
git commit -m "feat: add structured api log smoke"
```

Expected: commit succeeds.

### Task 5: PDCA Check And Report

**Files:**
- Create: `docs/03-analysis/T17-observability-structured-logging.analysis.md`
- Create: `docs/04-report/T17-observability-structured-logging.report.md`
- Modify: `.bkit-memory.json`

- [ ] **Step 1: Run final verification**

Run targeted test, full backend test, and runtime smoke with a live backend log.

- [ ] **Step 2: Write analysis/report**

Record command evidence, success criteria checks, residual risks, and commits.

- [ ] **Step 3: Commit report**

Run:

```powershell
git add .bkit-memory.json docs/03-analysis/T17-observability-structured-logging.analysis.md docs/04-report/T17-observability-structured-logging.report.md
git commit -m "docs: record T17 observability PDCA"
```

Expected: commit succeeds.
