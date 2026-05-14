# T14 Stage/Soundboard/Premium Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build stage channel state transitions, soundboard play skeleton, premium entitlement gates, and UI smoke coverage.

**Architecture:** Backend `experience` module owns in-memory product-experience state. Boot controllers enforce guild/channel permissions before domain calls. Nuxt renders one deterministic panel that exercises stage, soundboard, and premium flows.

**Tech Stack:** Java 21, Spring Boot, JUnit/MockMvc, Nuxt 4, Pinia, Vitest, Playwright.

---

### Task 1: Backend Experience Domain

**Files:**
- Create: `backend/modules/experience/build.gradle.kts`
- Create: `backend/modules/experience/src/main/java/com/example/discord/experience/*.java`
- Test: `backend/modules/experience/src/test/java/com/example/discord/experience/InMemoryExperienceServiceTest.java`
- Modify: `settings.gradle.kts`

- [ ] Write failing tests for stage pending approval, approve speaker, move audience, entitlement gate, soundboard registration/play projection.
- [ ] Run `.\gradlew.bat :backend:modules:experience:test` and verify RED.
- [ ] Implement records/enums and `InMemoryExperienceService`.
- [ ] Run `.\gradlew.bat :backend:modules:experience:test --rerun-tasks` and verify GREEN.
- [ ] Commit `feat: add experience domain`.

### Task 2: Backend Experience REST Adapter

**Files:**
- Modify: `backend/boot/build.gradle.kts`
- Create: `backend/boot/src/main/java/com/example/discord/experience/ExperienceConfiguration.java`
- Create: `backend/boot/src/main/java/com/example/discord/experience/StageController.java`
- Create: `backend/boot/src/main/java/com/example/discord/experience/SoundboardController.java`
- Create: `backend/boot/src/main/java/com/example/discord/experience/PremiumController.java`
- Test: `backend/boot/src/test/java/com/example/discord/experience/ExperienceControllerTest.java`

- [ ] Write failing MockMvc tests for stage moderator gate, audience approval, soundboard permission, and premium feature check.
- [ ] Run `.\gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest` and verify RED.
- [ ] Implement configuration/controllers with `InMemoryGuildService` permission checks.
- [ ] Run targeted test and full `.\gradlew.bat test`.
- [ ] Commit `feat: add experience api`.

### Task 3: Nuxt Experience Panel

**Files:**
- Create: `apps/web/components/shell/ExperiencePanel.vue`
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/pages/app.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] Add failing component/E2E tests for stage request/approve/audience, soundboard play, and premium entitlement gate.
- [ ] Run `npm run test -- --run tests/components/app-shell.test.ts` and verify RED.
- [ ] Implement store-backed experience state and panel UI.
- [ ] Run component test, `npm run build`, and `npm run e2e -- tests/e2e/app-shell.spec.ts`.
- [ ] Commit `feat: add experience operations ui`.

### Task 4: T14 PDCA Check And Report

**Files:**
- Create: `docs/03-analysis/T14-stage-soundboard-premium.analysis.md`
- Create: `docs/04-report/T14-stage-soundboard-premium.report.md`
- Create: `docs/05-feedback/T14-stage-soundboard-premium.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run full backend/frontend verification.
- [ ] Record design match, skeleton limitations, failure feedback, and command evidence.
- [ ] Commit `docs: record T14 stage soundboard premium PDCA`.
