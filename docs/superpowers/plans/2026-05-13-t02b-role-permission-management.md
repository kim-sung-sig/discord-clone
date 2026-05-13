# T02B Role/Permission Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add role/member/channel-overwrite management APIs and a Nuxt role-permission panel for the T02 guild/channel foundation.

**Architecture:** Backend remains a modular Spring Boot monorepo with in-memory guild storage for this slice. Frontend keeps static Pinia state but models roles and overwrites explicitly so later API integration can replace seed data without component rewrites.

**Tech Stack:** Java 21, Spring Boot, Gradle, JUnit/MockMvc, Nuxt 3, Pinia, Vitest, Playwright.

---

## Task 1: Backend Role And Overwrite API

**Files:**
- Modify: `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java`
- Modify: `backend/modules/guild/src/main/java/com/example/discord/guild/Guild.java`
- Modify: `backend/modules/guild/src/main/java/com/example/discord/guild/GuildMember.java`
- Modify: `backend/boot/src/main/java/com/example/discord/guild/GuildController.java`
- Modify: `backend/boot/src/test/java/com/example/discord/guild/GuildControllerTest.java`

- [ ] **Step 1: Write failing REST tests**

Add tests proving role create/update/assign/overwrite and null permission validation.

- [ ] **Step 2: Verify RED**

Run: `.\gradlew.bat :backend:boot:test --tests com.example.discord.guild.GuildControllerTest`

Expected: fails because role/overwrite endpoints are not implemented.

- [ ] **Step 3: Implement minimal API and validation**

Expose role list/create, permission replace, member assignment, and overwrite replace endpoints. Convert permission names to `PermissionSet` and reject null permission lists.

- [ ] **Step 4: Verify GREEN**

Run: `.\gradlew.bat :backend:boot:test --tests com.example.discord.guild.GuildControllerTest`

Expected: `BUILD SUCCESSFUL`.

## Task 2: Frontend Role Permission Panel

**Files:**
- Modify: `apps/web/stores/shell.ts`
- Create: `apps/web/components/shell/RolePermissionPanel.vue`
- Modify: `apps/web/app.vue`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] **Step 1: Write failing component/e2e assertions**

Assert the shell renders a role management panel with moderator permissions, member role assignment, and active channel overwrite summary.

- [ ] **Step 2: Verify RED**

Run: `npm run test -w apps/web -- --run tests/components/app-shell.test.ts`

Expected: fails because `role-permission-panel` is missing.

- [ ] **Step 3: Implement minimal store and panel**

Add role, member, and overwrite state to Pinia; render the panel in the app shell.

- [ ] **Step 4: Verify GREEN**

Run: `npm run test -w apps/web -- --run tests/components/app-shell.test.ts`

Expected: component tests pass.

## Task 3: QA And PDCA Closure

**Files:**
- Create: `docs/03-analysis/T02B-role-permission-management.analysis.md`
- Create: `docs/04-report/T02B-role-permission-management.report.md`
- Create: `docs/05-feedback/T02B-role-permission-management.feedback.md`

- [ ] **Step 1: Run full gates**

Run backend full tests, frontend component tests, e2e, build, and compose config.

- [ ] **Step 2: Document results**

Write Check/Report/Feedback docs with command evidence and unresolved risks.

- [ ] **Step 3: Commit**

Commit with message `feat: add role permission management slice`.

