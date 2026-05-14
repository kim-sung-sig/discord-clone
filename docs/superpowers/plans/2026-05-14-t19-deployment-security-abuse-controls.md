# T19 Deployment Security/Abuse Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Nuxt HTML deployment security headers and API rate limiting for high-risk auth/write endpoints.

**Architecture:** Spring Boot uses a `RateLimitStore` port from `OperationalHardeningFilter`, with an in-memory implementation for local tests and Redis parity documented for production. Nuxt uses a Nitro middleware plus pure header map so security headers are testable without launching a server.

**Tech Stack:** Spring Boot 3.3, Servlet filter, Micrometer, MockMvc, Nuxt 4, Nitro middleware, Vitest.

---

### Task 1: Planning

**Files:**
- Create: `docs/01-plan/features/T19-deployment-security-abuse-controls.plan.md`
- Create: `docs/02-design/features/T19-deployment-security-abuse-controls.design.md`
- Create: `docs/02-design/features/T19-redis-rate-limit-store.design.md`
- Create: `docs/superpowers/plans/2026-05-14-t19-deployment-security-abuse-controls.md`
- Modify: `.bkit-memory.json`

- [ ] Commit planning docs with `docs: plan T19 deployment security abuse controls`.

### Task 2: Backend Rate Limiter

**Files:**
- Create: `backend/boot/src/main/java/com/example/discord/ops/RateLimitKey.java`
- Create: `backend/boot/src/main/java/com/example/discord/ops/RateLimitPolicy.java`
- Create: `backend/boot/src/main/java/com/example/discord/ops/RateLimitDecision.java`
- Create: `backend/boot/src/main/java/com/example/discord/ops/RateLimitStore.java`
- Create: `backend/boot/src/main/java/com/example/discord/ops/InMemoryRateLimitStore.java`
- Create: `backend/boot/src/main/java/com/example/discord/ops/ApiRateLimitPolicy.java`
- Modify: `backend/boot/src/main/java/com/example/discord/ops/OperationalHardeningFilter.java`
- Modify: `backend/boot/src/test/java/com/example/discord/ops/OperationalHardeningFilterTest.java`

- [ ] Write failing tests for auth login IP limit, message bearer-subject limit, invite normalized path limit, gateway identify limit, and safe 429 response headers/body.
- [ ] Run `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks` and verify RED.
- [ ] Implement the rate limiter port, local store, policy matcher, subject extraction, and filter rejection response.
- [ ] Run the same test and verify GREEN.
- [ ] Commit with `feat: add api abuse rate limits`.

### Task 3: Nuxt HTML Security Headers

**Files:**
- Create: `apps/web/server/utils/security-headers.ts`
- Create: `apps/web/server/middleware/security-headers.ts`
- Create: `apps/web/tests/components/security-headers.test.ts`

- [ ] Write failing Vitest assertions for CSP, frame, content-type, referrer, and permissions headers.
- [ ] Run `npm run test -- --run tests/components/security-headers.test.ts` and verify RED.
- [ ] Implement the pure header map and Nitro middleware.
- [ ] Run the same test and verify GREEN.
- [ ] Run `npm run build`.
- [ ] Commit with `feat: add nuxt html security headers`.

### Task 4: Check/Report

**Files:**
- Create: `docs/03-analysis/T19-deployment-security-abuse-controls.analysis.md`
- Create: `docs/04-report/T19-deployment-security-abuse-controls.report.md`
- Modify: `.bkit-memory.json`

- [ ] Run final backend targeted, frontend targeted, backend full, frontend full, and Nuxt build commands.
- [ ] Record evidence, success criteria, failure criteria, and residual risks.
- [ ] Commit with `docs: record T19 deployment security PDCA`.
