# T12 Onboarding/AutoMod/Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build onboarding role assignment, AutoMod pre-persist blocking, audit logs, and a Nuxt moderation panel.

**Architecture:** Add an in-memory moderation module that owns onboarding, AutoMod rules, decisions, and audit records. Boot REST adapters call it, and `MessageController.create` invokes AutoMod before message persistence.

**Tech Stack:** Java 21, Spring Boot, JUnit/MockMvc, Nuxt 4, Pinia, Vitest, Playwright.

---

### Task 1: Backend Moderation Domain

**Files:**
- Create: `backend/modules/moderation/build.gradle.kts`
- Create: `backend/modules/moderation/src/main/java/com/example/discord/moderation/*.java`
- Test: `backend/modules/moderation/src/test/java/com/example/discord/moderation/InMemoryModerationServiceTest.java`
- Modify: `settings.gradle.kts`

- [ ] Write failing tests for onboarding role grants, keyword AutoMod block, and audit append.
- [ ] Run `.\gradlew.bat :backend:modules:moderation:test` and verify RED.
- [ ] Implement records/enums/service with deterministic in-memory maps.
- [ ] Run `.\gradlew.bat :backend:modules:moderation:test --rerun-tasks` and verify GREEN.
- [ ] Commit `feat: add moderation domain`.

### Task 2: Backend REST And Message AutoMod Gate

**Files:**
- Modify: `backend/boot/build.gradle.kts`
- Create: `backend/boot/src/main/java/com/example/discord/moderation/ModerationConfiguration.java`
- Create: `backend/boot/src/main/java/com/example/discord/moderation/ModerationController.java`
- Test: `backend/boot/src/test/java/com/example/discord/moderation/ModerationControllerTest.java`
- Modify: `backend/boot/src/main/java/com/example/discord/message/MessageController.java`

- [ ] Write failing MockMvc tests for onboarding answer role assignment, AutoMod blocked message absent from list, and audit log after rule creation.
- [ ] Run `.\gradlew.bat :backend:boot:test --tests com.example.discord.moderation.ModerationControllerTest` and verify RED.
- [ ] Add moderation bean/controller and inject moderation service into message create path before `messageService.create`.
- [ ] Run targeted MockMvc test and full `.\gradlew.bat test`.
- [ ] Commit `feat: add moderation rest automod gate`.

### Task 3: Nuxt Moderation UI

**Files:**
- Create: `apps/web/components/shell/ModerationPanel.vue`
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/pages/app.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] Add failing component/E2E tests for onboarding answer, AutoMod blocked status, and audit log visibility.
- [ ] Run `npm run test -- --run tests/components/app-shell.test.ts` and verify RED.
- [ ] Implement store state/actions and `ModerationPanel` with hydration guards.
- [ ] Run component test, `npm run build`, and `npm run e2e -- tests/e2e/app-shell.spec.ts`.
- [ ] Commit `feat: add moderation operations ui`.

### Task 4: T12 PDCA Check And Report

**Files:**
- Create: `docs/03-analysis/T12-onboarding-automod-audit.analysis.md`
- Create: `docs/04-report/T12-onboarding-automod-audit.report.md`
- Create: `docs/05-feedback/T12-onboarding-automod-audit.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run full backend/frontend verification.
- [ ] Record design match, gap log, residual risks, and command evidence.
- [ ] Commit `docs: record T12 onboarding automod audit PDCA`.
