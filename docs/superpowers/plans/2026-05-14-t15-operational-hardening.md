# T15 Operational Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add request correlation, API security headers, frontend request-id propagation, and regression evidence.

**Architecture:** Spring Boot adds one API-only servlet filter for hardening headers and request id handling. Nuxt API client adds request-id propagation while preserving auth/content-type behavior.

**Tech Stack:** Java 21, Spring Boot, JUnit/MockMvc, Nuxt 4, Vitest.

---

### Task 1: Backend Operational Hardening Filter

**Files:**
- Create: `backend/boot/src/main/java/com/example/discord/ops/OperationalHardeningFilter.java`
- Test: `backend/boot/src/test/java/com/example/discord/ops/OperationalHardeningFilterTest.java`

- [ ] Write failing MockMvc tests for generated request id, safe request id echo, unsafe id sanitization, and security headers.
- [ ] Run `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest` and verify RED.
- [ ] Implement API-only servlet filter.
- [ ] Run targeted test and verify GREEN.
- [ ] Commit `feat: add api operational hardening`.

### Task 2: Frontend API Request ID Propagation

**Files:**
- Modify: `apps/web/services/discord-api.ts`
- Modify: `apps/web/tests/components/shell-contracts.test.ts`

- [ ] Add failing Vitest assertions for provided and generated `X-Request-Id`.
- [ ] Run `npm run test -- --run tests/components/shell-contracts.test.ts` and verify RED.
- [ ] Implement `requestId` option and fallback generation.
- [ ] Run targeted test and verify GREEN.
- [ ] Commit `feat: add frontend request correlation`.

### Task 3: T15 PDCA Check And Report

**Files:**
- Create: `docs/03-analysis/T15-operational-hardening.analysis.md`
- Create: `docs/04-report/T15-operational-hardening.report.md`
- Create: `docs/05-feedback/T15-operational-hardening.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run `.\gradlew.bat test`.
- [ ] Run `npm run test -- --run`.
- [ ] Run `npm run build`.
- [ ] Run `npm run e2e -- tests/e2e/app-shell.spec.ts`.
- [ ] Record command evidence and limitations.
- [ ] Commit `docs: record T15 operational hardening PDCA`.
