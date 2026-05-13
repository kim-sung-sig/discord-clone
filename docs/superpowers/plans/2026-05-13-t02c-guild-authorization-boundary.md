# T02C Guild Authorization Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Require authenticated guild owners or members with matching manage permissions for guild/channel/role mutation APIs.

**Architecture:** Keep the current modular Spring Boot and in-memory guild service. Add a boot-level auth resolver and small service authorization methods without adding a full Spring Security filter chain yet.

**Tech Stack:** Java 21, Spring Boot MVC, JUnit/MockMvc, Gradle.

---

## Task 1: Auth Resolver

**Files:**
- Create: `backend/boot/src/main/java/com/example/discord/auth/AuthenticatedUserResolver.java`
- Modify: `backend/boot/src/test/java/com/example/discord/guild/GuildControllerTest.java`

- [ ] **Step 1: Write failing test**

Add MockMvc test expecting `POST /api/guilds` without Authorization to return `401`.

- [ ] **Step 2: Verify RED**

Run: `.\gradlew.bat :backend:boot:test --tests com.example.discord.guild.GuildControllerTest`

Expected: test fails because guild creation currently does not require auth.

- [ ] **Step 3: Implement resolver and create-guild auth**

Resolve bearer token through auth store and token service. Use authenticated user id as guild owner.

- [ ] **Step 4: Verify GREEN**

Run targeted guild controller test.

## Task 2: Role And Channel Authorization

**Files:**
- Modify: `backend/modules/permission/src/main/java/com/example/discord/permission/Permission.java`
- Modify: `backend/modules/guild/src/main/java/com/example/discord/guild/InMemoryGuildService.java`
- Modify: `backend/boot/src/main/java/com/example/discord/guild/GuildController.java`
- Modify: `backend/boot/src/test/java/com/example/discord/guild/GuildControllerTest.java`

- [ ] **Step 1: Write failing tests**

Add tests for missing token `401`, non-owner `403`, `MANAGE_ROLES` delegate success, and `MANAGE_CHANNELS` delegate success.

- [ ] **Step 2: Verify RED**

Run targeted guild controller test.

- [ ] **Step 3: Implement permission checks**

Owner bypasses. Non-owner must have matching permission through assigned guild roles.

- [ ] **Step 4: Verify GREEN**

Run targeted guild controller test.

## Task 3: QA And PDCA Closure

**Files:**
- Create: `docs/03-analysis/T02C-guild-authorization-boundary.analysis.md`
- Create: `docs/04-report/T02C-guild-authorization-boundary.report.md`
- Create: `docs/05-feedback/T02C-guild-authorization-boundary.feedback.md`

- [ ] **Step 1: Run full gates**

Run backend full tests, frontend component tests, e2e, build, and compose config.

- [ ] **Step 2: Document results**

Record verification evidence and remaining risks.

- [ ] **Step 3: Commit**

Commit with message `feat: enforce guild mutation authorization`.

