# T04 Message Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add message lifecycle backend behavior and chat composer/metadata UI.

**Architecture:** Backend adds a focused message module and boot adapter that reuses guild/channel permission checks. Frontend extends the existing static shell store and chat viewport so UX can be verified before gateway/API integration.

**Tech Stack:** Java 21, Spring Boot MVC, Gradle, JUnit/MockMvc, Nuxt 3, Pinia, Vitest, Playwright.

---

## Task 1: Backend Message Core

**Files:**
- Create: `backend/modules/message/**`
- Modify: `settings.gradle.kts`
- Modify: `backend/boot/build.gradle.kts`
- Modify: `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java`
- Create: `backend/boot/src/main/java/com/example/discord/message/MessageController.java`
- Create: `backend/boot/src/main/java/com/example/discord/message/MessageConfiguration.java`
- Create: `backend/boot/src/test/java/com/example/discord/message/MessageControllerTest.java`
- Modify: `backend/boot/src/test/java/com/example/discord/architecture/ArchitectureTest.java`

- [ ] **Step 1: Write failing service tests**

Cover cursor pagination, mention extraction, edit history, tombstone delete, pin/unpin, and search.

- [ ] **Step 2: Verify RED**

Run: `.\gradlew.bat :backend:modules:message:test`

- [ ] **Step 3: Implement minimal message module**

Implement immutable `Message`, `MessagePage`, commands, exceptions, and synchronized `InMemoryMessageService`.

- [ ] **Step 4: Write failing MockMvc tests**

Cover `SEND_MESSAGES` denied create, `VIEW_CHANNEL` denied list/search, author edit/delete, `MANAGE_MESSAGES` delete/pin.

- [ ] **Step 5: Implement REST adapter and guild permission checks**

Add boot controller/configuration and extend guild service with channel permission checks.

- [ ] **Step 6: Verify GREEN**

Run: `.\gradlew.bat :backend:modules:message:test :backend:boot:test --tests com.example.discord.message.MessageControllerTest --tests com.example.discord.architecture.ArchitectureTest`

## Task 2: Frontend Chat Composer And Metadata

**Files:**
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/components/shell/ChatViewport.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] **Step 1: Write failing component/e2e assertions**

Assert pinned label, edited marker, deleted tombstone, mention chip, composer input, and send action.

- [ ] **Step 2: Verify RED**

Run: `npm run test -w apps/web -- --run tests/components/app-shell.test.ts`

- [ ] **Step 3: Implement minimal store/UI changes**

Extend shell seed data and `ChatViewport` with composer and metadata rendering.

- [ ] **Step 4: Verify GREEN**

Run component and e2e tests.

## Task 3: QA And PDCA Closure

**Files:**
- Create: `docs/03-analysis/T04-message-core.analysis.md`
- Create: `docs/04-report/T04-message-core.report.md`
- Create: `docs/05-feedback/T04-message-core.feedback.md`

- [ ] **Step 1: Run full gates**

Run backend full tests, frontend component tests, e2e, build, and compose config.

- [ ] **Step 2: Review and fix**

Run backend/frontend reviews and close findings.

- [ ] **Step 3: Commit**

Commit backend, frontend, and PDCA docs as separate logical commits.
