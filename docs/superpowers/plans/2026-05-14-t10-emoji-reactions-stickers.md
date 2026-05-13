# T10 Emoji/Reactions/Stickers Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add custom emoji/sticker skeletons, expression permissions, idempotent reactions, and Nuxt reaction UI.

**Architecture:** Introduce a backend `expression` module for emoji/sticker/reaction state, extend guild permissions with `MANAGE_EXPRESSIONS`, expose REST endpoints, and add a store-backed Nuxt reaction bar/panel. Counts derive from user sets to avoid duplicate increments.

**Tech Stack:** Java 21, Spring Boot MockMvc, JUnit 5, AssertJ, Nuxt 4, Pinia, Vitest, Playwright.

---

### Task 1: Backend Expression Domain

**Files:**
- Create: `backend/modules/expression/build.gradle.kts`
- Create: `backend/modules/expression/src/main/java/com/example/discord/expression/*.java`
- Test: `backend/modules/expression/src/test/java/com/example/discord/expression/InMemoryExpressionServiceTest.java`
- Modify: `settings.gradle.kts`
- Modify: `backend/modules/permission/src/main/java/com/example/discord/permission/Permission.java`
- Modify: `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java`

- [ ] Write failing unit tests for duplicate reaction idempotency, reaction remove safety, emoji/sticker validation.
- [ ] Run `./gradlew.bat :backend:modules:expression:test` and verify RED.
- [ ] Implement expression domain and permission extension.
- [ ] Run targeted module test and verify GREEN.
- [ ] Commit `feat: add expression domain module`.

### Task 2: Backend Expression REST Adapter

**Files:**
- Create: `backend/boot/src/main/java/com/example/discord/expression/ExpressionConfiguration.java`
- Create: `backend/boot/src/main/java/com/example/discord/expression/ExpressionController.java`
- Test: `backend/boot/src/test/java/com/example/discord/expression/ExpressionControllerTest.java`
- Modify: `backend/boot/build.gradle.kts`

- [ ] Write failing MockMvc tests for emoji permission, reaction idempotency, sticker skeleton.
- [ ] Run targeted boot test and verify RED.
- [ ] Implement controller/configuration and DTOs.
- [ ] Run targeted boot test and verify GREEN.
- [ ] Commit `feat: expose expression rest api`.

### Task 3: Nuxt Reaction UI

**Files:**
- Create: `apps/web/components/shell/ReactionBar.vue`
- Create: `apps/web/components/shell/ExpressionPanel.vue`
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/components/shell/ChatViewport.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] Write failing component tests for reaction count/toggle and expression panel.
- [ ] Write failing Playwright reaction add/remove smoke.
- [ ] Implement store actions and UI components.
- [ ] Run targeted Vitest/E2E and verify GREEN.
- [ ] Commit `feat: add reaction expression ui`.

### Task 4: T10 Verification and PDCA

**Files:**
- Create: `docs/03-analysis/T10-emoji-reactions-stickers.analysis.md`
- Create: `docs/04-report/T10-emoji-reactions-stickers.report.md`
- Create: `docs/05-feedback/T10-emoji-reactions-stickers.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run full gates and document evidence.
- [ ] Commit `docs: record T10 emoji reactions stickers PDCA`.
