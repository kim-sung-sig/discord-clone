# T20 Premium Billing/Entitlement Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make premium feature gates lifecycle-aware, idempotent, provider-bound, and audit-visible without pretending to process real payments.

**Architecture:** The experience module owns entitlement domain state through an `EntitlementStore` port. Boot wires a deterministic local billing provider and audit publisher; provider failure never creates active entitlements.

**Tech Stack:** Spring Boot 3.3, Java records/enums/interfaces, MockMvc, AssertJ, Flyway SQL schema.

---

### Task 1: Planning

**Files:**
- Create: `docs/01-plan/features/T20-premium-billing-entitlement-persistence.plan.md`
- Create: `docs/02-design/features/T20-premium-billing-entitlement-persistence.design.md`
- Create: `docs/superpowers/plans/2026-05-15-t20-premium-billing-entitlement-persistence.md`
- Modify: `.bkit-memory.json`

- [ ] Commit planning docs with `docs: plan T20 premium billing entitlement persistence`.

### Task 2: Entitlement Lifecycle Domain

**Files:**
- Create: `backend/modules/experience/src/main/java/com/example/discord/experience/EntitlementStatus.java`
- Create: `backend/modules/experience/src/main/java/com/example/discord/experience/EntitlementStore.java`
- Create: `backend/modules/experience/src/main/java/com/example/discord/experience/InMemoryEntitlementStore.java`
- Modify: `backend/modules/experience/src/main/java/com/example/discord/experience/Entitlement.java`
- Modify: `backend/modules/experience/src/main/java/com/example/discord/experience/InMemoryExperienceService.java`
- Modify: `backend/modules/experience/src/test/java/com/example/discord/experience/InMemoryExperienceServiceTest.java`

- [ ] Write failing tests for active, expired, canceled, and duplicate provider subscription behavior.
- [ ] Run module tests and verify RED.
- [ ] Implement lifecycle model and store port.
- [ ] Run module tests and verify GREEN.
- [ ] Commit with `feat: add premium entitlement lifecycle`.

### Task 3: Billing Provider and Controller Safety

**Files:**
- Create: `backend/modules/experience/src/main/java/com/example/discord/experience/BillingProvider.java`
- Create: `backend/modules/experience/src/main/java/com/example/discord/experience/BillingCheckoutCommand.java`
- Create: `backend/modules/experience/src/main/java/com/example/discord/experience/BillingCheckoutResult.java`
- Create: `backend/modules/experience/src/main/java/com/example/discord/experience/LocalTestBillingProvider.java`
- Modify: `backend/boot/src/main/java/com/example/discord/experience/ExperienceConfiguration.java`
- Modify: `backend/boot/src/main/java/com/example/discord/experience/PremiumController.java`
- Modify: `backend/boot/src/test/java/com/example/discord/experience/ExperienceControllerTest.java`

- [ ] Write failing MockMvc tests for provider failure, duplicate grant idempotency, expiry gate, and self-only policy.
- [ ] Run targeted controller tests and verify RED.
- [ ] Implement provider port and safer grant flow.
- [ ] Run targeted controller tests and verify GREEN.
- [ ] Commit with `feat: gate premium grants through billing provider`.

### Task 4: Persistence Schema and Audit

**Files:**
- Create: `backend/boot/src/main/resources/db/migration/V5__premium_entitlements.sql`
- Modify: `backend/modules/moderation/src/main/java/com/example/discord/moderation/AuditLogAction.java`
- Modify: `backend/modules/moderation/src/main/java/com/example/discord/moderation/InMemoryModerationService.java`
- Modify: `backend/boot/src/main/java/com/example/discord/experience/PremiumController.java`
- Modify: `backend/boot/src/test/java/com/example/discord/experience/ExperienceControllerTest.java`

- [ ] Write failing tests for premium grant audit visibility through `/api/guilds/{guildId}/audit-logs`.
- [ ] Add Flyway schema for `premium_entitlements`.
- [ ] Add append-only premium audit action and call it after successful grant.
- [ ] Run targeted tests and full backend tests.
- [ ] Commit with `feat: audit premium entitlement changes`.

### Task 5: Check/Report

**Files:**
- Create: `docs/03-analysis/T20-premium-billing-entitlement-persistence.analysis.md`
- Create: `docs/04-report/T20-premium-billing-entitlement-persistence.report.md`
- Modify: `.bkit-memory.json`

- [ ] Record RED/GREEN evidence, success/failure criteria, and residual risks.
- [ ] Commit with `docs: record T20 premium billing PDCA`.
