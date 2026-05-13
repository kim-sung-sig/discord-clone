# T01 Identity/User Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend identity and user foundation modules with tested password hashing, access token expiry, refresh rotation, login lockout, and profile validation.

**Architecture:** Add `identity` and `user` as Gradle modules under `backend/modules`. Keep behavior pure and persistence-free for this slice so the security policy can be tested before API/database integration.

**Tech Stack:** Java 21, Gradle Kotlin DSL, JUnit 5, AssertJ, Spring Security Crypto, HMAC-SHA256.

---

## Tasks

- [ ] Register `:backend:modules:identity` and `:backend:modules:user` in Gradle.
- [ ] Write RED tests for identity value objects, password hashing, access token expiry, refresh rotation, and lockout.
- [ ] Implement the minimum identity classes to make tests pass.
- [ ] Write RED tests for username/profile/privacy defaults.
- [ ] Implement the minimum user classes to make tests pass.
- [ ] Wire boot module dependencies and update ArchUnit package rule.
- [ ] Run full verification gate.

## Verification

```powershell
.\gradlew.bat test
npm run test -w apps/web -- --run tests/components/app-shell.test.ts
npm run e2e -w apps/web
npm run build -w apps/web
docker compose -f infra/docker/docker-compose.yml config
```

## Self-Review

- Spec coverage: Covers the backend foundation required by T01 security acceptance criteria.
- Placeholder scan: no unresolved placeholder markers.
- Type consistency: identity classes use `com.example.discord.identity`; user classes use `com.example.discord.user`.

