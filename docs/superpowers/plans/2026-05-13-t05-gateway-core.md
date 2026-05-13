# T05 Gateway Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add gateway session/event semantics and a Nuxt gateway status surface.

**Architecture:** Backend adds a focused gateway module and HTTP gateway adapter that can later be replaced by WebSocket transport. Frontend adds deterministic gateway state handling before a live socket connection exists.

**Tech Stack:** Java 21, Spring Boot MVC, Gradle, JUnit/MockMvc, Nuxt 3, Pinia, Vitest, Playwright.

---

## Task 1: Backend Gateway Core

**Files:**
- Create: `backend/modules/gateway/**`
- Modify: `settings.gradle.kts`
- Modify: `backend/boot/build.gradle.kts`
- Modify: `backend/boot/src/test/java/com/example/discord/architecture/ArchitectureTest.java`
- Modify: `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java`
- Create: `backend/boot/src/main/java/com/example/discord/gateway/GatewayController.java`
- Create: `backend/boot/src/main/java/com/example/discord/gateway/GatewayConfiguration.java`
- Create: `backend/boot/src/test/java/com/example/discord/gateway/GatewayControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Cover identify READY, heartbeat ACK, heartbeat timeout, sequence monotonicity, resume after sequence, duplicate filtering, and unauthorized channel filtering.

- [ ] **Step 2: Verify RED**

Run: `.\gradlew.bat :backend:modules:gateway:test`

- [ ] **Step 3: Implement gateway module**

Implement immutable session/event records and synchronized `InMemoryGatewayService`.

- [ ] **Step 4: Write failing MockMvc tests**

Cover identify, heartbeat owner check, resume from `lastSeq`, event polling, and hidden channel event filtering.

- [ ] **Step 5: Implement boot adapter/configuration**

Add gateway controller and wire it to auth/guild services.

- [ ] **Step 6: Verify GREEN**

Run: `.\gradlew.bat :backend:modules:gateway:test :backend:boot:test --tests com.example.discord.gateway.GatewayControllerTest --tests com.example.discord.architecture.ArchitectureTest`

## Task 2: Frontend Gateway Store And Status UI

**Files:**
- Modify: `apps/web/stores/shell.ts`
- Create or modify: `apps/web/components/shell/GatewayStatusPanel.vue`
- Modify: `apps/web/pages/app.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] **Step 1: Write failing component/e2e assertions**

Assert READY status, last sequence, heartbeat ack label, resumed label, and duplicate event guard.

- [ ] **Step 2: Verify RED**

Run: `npm run test -w apps/web -- --run tests/components/app-shell.test.ts`

- [ ] **Step 3: Implement store/UI**

Add gateway state/actions and status panel with deterministic seed data.

- [ ] **Step 4: Verify GREEN**

Run component and e2e tests.

## Task 3: QA And PDCA Closure

**Files:**
- Create: `docs/03-analysis/T05-gateway-core.analysis.md`
- Create: `docs/04-report/T05-gateway-core.report.md`
- Create: `docs/05-feedback/T05-gateway-core.feedback.md`

- [ ] **Step 1: Run full gates**

Run backend full tests, frontend component tests, e2e, build, and compose config.

- [ ] **Step 2: Review and fix**

Run backend/frontend reviews and close findings.

- [ ] **Step 3: Commit**

Commit backend, frontend, and PDCA docs as separate logical commits.
