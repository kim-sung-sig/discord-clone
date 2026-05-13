# T08 Presence/Typing/Read State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic presence, typing expiry, read marker, unread count, and Nuxt status/badge UI.

**Architecture:** Implement a backend `presence` module with a Redis-compatible TTL store port and in-memory adapter, expose REST endpoints through boot, and extend the existing Nuxt shell with status, typing, and unread indicators. All time-based behavior uses injected clocks for TDD.

**Tech Stack:** Java 21, Spring Boot MockMvc, JUnit 5, AssertJ, Nuxt 4, Pinia, Vitest, Playwright.

---

### Task 1: Backend Presence Domain

**Files:**
- Create: `backend/modules/presence/build.gradle.kts`
- Create: `backend/modules/presence/src/main/java/com/example/discord/presence/*.java`
- Test: `backend/modules/presence/src/test/java/com/example/discord/presence/InMemoryPresenceServiceTest.java`
- Modify: `settings.gradle.kts`

- [ ] Write failing unit tests for TTL expiry to offline, typing expiry, and deterministic unread count.
- [ ] Run `./gradlew.bat :backend:modules:presence:test` and verify RED.
- [ ] Implement `PresenceTtlStore`, `InMemoryRedisPresenceTtlStore`, and `InMemoryPresenceService`.
- [ ] Run targeted module test and verify GREEN.
- [ ] Commit `feat: add presence domain module`.

### Task 2: Backend Presence REST Adapter

**Files:**
- Create: `backend/boot/src/main/java/com/example/discord/presence/PresenceConfiguration.java`
- Create: `backend/boot/src/main/java/com/example/discord/presence/PresenceController.java`
- Test: `backend/boot/src/test/java/com/example/discord/presence/PresenceControllerTest.java`
- Modify: `backend/boot/build.gradle.kts`

- [ ] Write failing MockMvc tests for status update/read, typing expiry, and read marker/unread endpoint.
- [ ] Run `./gradlew.bat :backend:boot:test --tests com.example.discord.presence.PresenceControllerTest` and verify RED.
- [ ] Implement controller/configuration.
- [ ] Run targeted boot test and verify GREEN.
- [ ] Commit `feat: expose presence rest api`.

### Task 3: Nuxt Presence UI

**Files:**
- Create: `apps/web/components/shell/PresenceBadge.vue`
- Create: `apps/web/components/shell/TypingIndicator.vue`
- Create: `apps/web/components/shell/UnreadBadge.vue`
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/components/shell/MemberSidebar.vue`
- Modify: `apps/web/components/shell/ChannelSidebar.vue`
- Modify: `apps/web/components/social/DmSidebar.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] Write failing component tests for presence badge, typing indicator, unread badge, and mark-read behavior.
- [ ] Write failing Playwright smoke for unread clear and typing visibility.
- [ ] Implement UI components and Pinia state/actions.
- [ ] Run targeted Vitest/E2E and verify GREEN.
- [ ] Commit `feat: add presence typing unread ui`.

### Task 4: T08 Verification and PDCA

**Files:**
- Create: `docs/03-analysis/T08-presence-typing-read-state.analysis.md`
- Create: `docs/04-report/T08-presence-typing-read-state.report.md`
- Create: `docs/05-feedback/T08-presence-typing-read-state.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run full gates and document evidence.
- [ ] Commit `docs: record T08 presence typing read PDCA`.
