# T02 Guild/Channel/Permission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the first guild/channel/permission foundation slice with tested permission calculation, visible channel API, and frontend shell state alignment.

**Architecture:** Extend the existing modular backend with `guild` and `channel` modules and enhance `permission` with a calculator. Keep storage in-memory for this slice and add REST endpoints in the boot module. Frontend remains API-free but aligns its store with guild/channel concepts.

**Tech Stack:** Java 21, Spring Boot MVC, JUnit 5, AssertJ, MockMvc, Nuxt 4, Pinia, Vitest, Playwright.

---

## Worker Split

### Backend worker

Owns:

- `settings.gradle.kts`
- `backend/boot/build.gradle.kts`
- `backend/boot/src/main/java/com/example/discord/guild/**`
- `backend/boot/src/test/java/com/example/discord/guild/**`
- `backend/modules/guild/**`
- `backend/modules/channel/**`
- `backend/modules/permission/**`

Required tests:

- permission calculator tests
- guild aggregate/service tests
- guild controller tests

### Frontend worker

Owns:

- `apps/web/stores/shell.ts`
- `apps/web/components/shell/**`
- `apps/web/tests/components/app-shell.test.ts`
- `apps/web/tests/e2e/app-shell.spec.ts`

Required tests:

- app shell component tests still pass
- e2e shell tests still pass

## Verification

```powershell
.\gradlew.bat test --rerun-tasks
npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts
npm run e2e -w apps/web
npm run build -w apps/web
docker compose -f infra/docker/docker-compose.yml config
```

## Self-Review

- Spec coverage: Covers T02-A permission/guild/channel foundation, not full Discord guild management.
- Placeholder scan: no unresolved placeholder markers.
- Type consistency: backend packages use `com.example.discord.guild`, `com.example.discord.channel`, and `com.example.discord.permission`.

