# T07 Friendship/DM/Group DM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build testable friendship, block/privacy, direct DM, group DM, and group call skeleton behavior across Spring Boot and Nuxt.

**Architecture:** Add an independent backend `social` module for user relationship and private channel policy, expose it through boot REST endpoints, and extend the existing Nuxt shell with a DM sidebar backed by Pinia state. Keep persistence in-memory and isolate DM policy from guild permissions.

**Tech Stack:** Java 21, Spring Boot MockMvc, JUnit 5, AssertJ, Nuxt 4, Pinia, Vitest, Playwright.

---

### Task 1: Backend Social Domain

**Files:**
- Create: `backend/modules/social/build.gradle.kts`
- Create: `backend/modules/social/src/main/java/com/example/discord/social/*.java`
- Test: `backend/modules/social/src/test/java/com/example/discord/social/InMemorySocialServiceTest.java`
- Modify: `settings.gradle.kts`

- [ ] Write failing unit tests for friend request accept, blocked DM rejection, and group owner-only member changes.
- [ ] Run `./gradlew.bat :backend:modules:social:test` and verify failure before implementation.
- [ ] Implement `InMemorySocialService` records and invariants.
- [ ] Run `./gradlew.bat :backend:modules:social:test` and verify pass.
- [ ] Commit `feat: add social domain module`.

### Task 2: Backend Social REST Adapter

**Files:**
- Create: `backend/boot/src/main/java/com/example/discord/social/SocialConfiguration.java`
- Create: `backend/boot/src/main/java/com/example/discord/social/SocialController.java`
- Test: `backend/boot/src/test/java/com/example/discord/social/SocialControllerTest.java`
- Modify: `backend/boot/build.gradle.kts`

- [ ] Write failing MockMvc tests for friend accept, block prevents DM message, and group member add/remove authorization.
- [ ] Run `./gradlew.bat :backend:boot:test --tests com.example.discord.social.SocialControllerTest` and verify failure.
- [ ] Implement controller/configuration and request/response DTOs.
- [ ] Run targeted boot test and verify pass.
- [ ] Commit `feat: expose social REST api`.

### Task 3: Nuxt DM Shell

**Files:**
- Create: `apps/web/components/social/DmSidebar.vue`
- Create: `apps/web/components/social/DmSidebar.stories.ts`
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/pages/app.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/components/story-index.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] Write failing component tests for DM list, blocked state, group DM member add/remove, and group call skeleton.
- [ ] Write failing Playwright group DM smoke.
- [ ] Run targeted Vitest/E2E and verify failures.
- [ ] Implement Pinia social state/actions and `DmSidebar` UI.
- [ ] Run targeted Vitest/E2E and verify pass.
- [ ] Commit `feat: add nuxt dm shell`.

### Task 4: T07 Verification and PDCA

**Files:**
- Create: `docs/03-analysis/T07-friendship-dm-group-dm.analysis.md`
- Create: `docs/04-report/T07-friendship-dm-group-dm.report.md`
- Create: `docs/05-feedback/T07-friendship-dm-group-dm.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run full gates: `./gradlew.bat test --rerun-tasks`, `npm run test -w apps/web -- --run`, `npm run build -w apps/web`, `npm run e2e -w apps/web`.
- [ ] Document gaps, fixes, verification output, and non-blocking risks.
- [ ] Commit `docs: record T07 friendship dm PDCA`.
