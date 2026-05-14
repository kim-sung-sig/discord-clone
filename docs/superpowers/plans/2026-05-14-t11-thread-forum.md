# T11 Thread/Forum Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add public/private thread skeletons, archive/reopen lifecycle, forum tags/guidelines, parent permission inheritance, and Nuxt forum UI.

**Architecture:** Introduce a backend `thread` module for lifecycle/tag invariants, enforce parent channel permissions in the boot adapter through `InMemoryGuildService`, and add a store-backed Nuxt forum/thread panel. Domain time behavior uses injected clocks for auto-archive tests.

**Tech Stack:** Java 21, Spring Boot MockMvc, JUnit 5, AssertJ, Nuxt 4, Pinia, Vitest, Playwright.

---

### Task 1: Backend Thread Domain

**Files:**
- Create: `backend/modules/thread/build.gradle.kts`
- Create: `backend/modules/thread/src/main/java/com/example/discord/thread/*.java`
- Test: `backend/modules/thread/src/test/java/com/example/discord/thread/InMemoryThreadServiceTest.java`
- Modify: `settings.gradle.kts`
- Modify: `backend/modules/channel/src/main/java/com/example/discord/channel/ChannelType.java`

- [ ] Write failing unit tests for archive/reopen, archived write rejection, forum tag requirement, auto archive.
- [ ] Run `./gradlew.bat :backend:modules:thread:test` and verify RED.
- [ ] Implement thread/forum domain.
- [ ] Run targeted module test and verify GREEN.
- [ ] Commit `feat: add thread forum domain module`.

### Task 2: Backend Thread REST Adapter

**Files:**
- Create: `backend/boot/src/main/java/com/example/discord/thread/ThreadConfiguration.java`
- Create: `backend/boot/src/main/java/com/example/discord/thread/ThreadController.java`
- Test: `backend/boot/src/test/java/com/example/discord/thread/ThreadControllerTest.java`
- Modify: `backend/boot/build.gradle.kts`

- [ ] Write failing MockMvc tests for parent permission inheritance, archived write forbidden, forum tag requirement.
- [ ] Run targeted boot test and verify RED.
- [ ] Implement controller/configuration and DTOs.
- [ ] Run targeted boot test and verify GREEN.
- [ ] Commit `feat: expose thread forum rest api`.

### Task 3: Nuxt Forum UI

**Files:**
- Create: `apps/web/components/shell/ForumPanel.vue`
- Create: `apps/web/components/shell/ThreadList.vue`
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/pages/app.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] Write failing component tests for forum guidelines/tags, archived badge, reopen/write state.
- [ ] Write failing Playwright forum lifecycle smoke.
- [ ] Implement store actions and UI components.
- [ ] Run targeted Vitest/E2E and verify GREEN.
- [ ] Commit `feat: add forum thread ui`.

### Task 4: T11 Verification and PDCA

**Files:**
- Create: `docs/03-analysis/T11-thread-forum.analysis.md`
- Create: `docs/04-report/T11-thread-forum.report.md`
- Create: `docs/05-feedback/T11-thread-forum.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run full gates and document evidence.
- [ ] Commit `docs: record T11 thread forum PDCA`.
