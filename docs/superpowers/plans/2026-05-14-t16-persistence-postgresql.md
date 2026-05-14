# T16 Persistence/PostgreSQL Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move critical auth/guild/message/invite state from in-memory runtime state to PostgreSQL-backed repository adapters.

**Architecture:** Preserve existing domain behavior and introduce repository ports/adapters incrementally. Keep in-memory adapters for fast unit tests and add PostgreSQL integration tests for persistence-backed boot/runtime paths.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL, Flyway or Liquibase candidate, JUnit/MockMvc, Testcontainers or local Docker profile.

---

### Task 1: Persistence Architecture Decision

**Files:**
- Create: `docs/02-design/features/T16-persistence-postgresql.design.md`
- Modify: `backend/boot/build.gradle.kts`
- Modify: `settings.gradle.kts` if a shared persistence module is introduced.

- [ ] Decide Flyway vs Liquibase and document why.
- [ ] Decide Testcontainers vs local-only integration profile and document tradeoff.
- [ ] Define repository port boundaries for auth, guild, message, invite.
- [ ] Commit `docs: design T16 persistence postgresql`.

### Task 2: Database Bootstrap

**Files:**
- Create: `backend/boot/src/main/resources/db/migration/*.sql`
- Create/modify: `backend/boot/src/main/resources/application*.yml`
- Test: `backend/boot/src/test/java/com/example/discord/persistence/PersistenceBootstrapTest.java`

- [ ] Write failing test proving migration starts and core tables exist.
- [ ] Add migration dependency/config.
- [ ] Implement baseline tables for users/auth_sessions/guilds/channels/roles/messages/invites.
- [ ] Run targeted persistence bootstrap test.
- [ ] Commit `feat: add postgresql migration baseline`.

### Task 3: Auth/Guild Persistence Adapter

**Files:**
- Create/modify: auth/guild repository ports and adapters.
- Test: persistence integration tests for signup/login/guild/channel restart behavior.

- [ ] Add failing persistence tests for auth/guild restart survival.
- [ ] Implement adapters and boot wiring behind profile/config.
- [ ] Run targeted tests and `qa/api-smoke.ps1` against persistence profile.
- [ ] Commit `feat: persist auth and guild state`.

### Task 4: Message/Invite Persistence Adapter

**Files:**
- Create/modify: message/invite repository ports and adapters.
- Test: pagination, invite max-use, duplicate/race constraint tests.

- [ ] Add failing persistence tests for message cursor pagination and invite accept idempotency.
- [ ] Implement adapters and constraints.
- [ ] Run targeted tests and runtime smoke.
- [ ] Commit `feat: persist message and invite state`.

### Task 5: T16 PDCA Check And Report

**Files:**
- Create: `docs/03-analysis/T16-persistence-postgresql.analysis.md`
- Create: `docs/04-report/T16-persistence-postgresql.report.md`
- Create: `docs/05-feedback/T16-persistence-postgresql.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run full backend tests.
- [ ] Run frontend tests/build/e2e if frontend contracts changed.
- [ ] Run `qa/api-smoke.ps1` against persistence profile.
- [ ] Record gaps and follow-up tasks.
- [ ] Commit `docs: record T16 persistence postgresql PDCA`.
