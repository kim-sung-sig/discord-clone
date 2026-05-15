# T21 Audit/Security Actions Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand audit coverage and add a security alert skeleton for suspicious behavior decisions.

**Architecture:** `InMemoryModerationService` remains the audit/security boundary. Controllers append audit entries after successful privileged mutations, and AutoMod creates alerts before blocked messages can persist.

**Tech Stack:** Spring Boot 3.3, Java records/enums, MockMvc, AssertJ.

---

### Task 1: Planning

**Files:**
- Create: `docs/01-plan/features/T21-audit-security-actions-expansion.plan.md`
- Create: `docs/02-design/features/T21-audit-security-actions-expansion.design.md`
- Create: `docs/superpowers/plans/2026-05-15-t21-audit-security-actions-expansion.md`
- Modify: `.bkit-memory.json`

- [ ] Commit planning docs.

### Task 2: Audit Search and Coverage

**Files:**
- Modify: `backend/modules/moderation/src/main/java/com/example/discord/moderation/AuditLogAction.java`
- Modify: `backend/modules/moderation/src/main/java/com/example/discord/moderation/InMemoryModerationService.java`
- Modify: `backend/boot/src/main/java/com/example/discord/moderation/ModerationController.java`
- Modify controllers for guild/invite/message/stage audit hooks.
- Modify: `backend/boot/src/test/java/com/example/discord/moderation/ModerationControllerTest.java`

- [ ] Write failing tests for searchable role/invite/message/stage audit entries.
- [ ] Implement filters and audit hooks.
- [ ] Run targeted tests.

### Task 3: Security Alerts

**Files:**
- Create: `backend/modules/moderation/src/main/java/com/example/discord/moderation/SecurityAlert.java`
- Modify: `backend/modules/moderation/src/main/java/com/example/discord/moderation/InMemoryModerationService.java`
- Modify: `backend/boot/src/main/java/com/example/discord/moderation/ModerationController.java`
- Modify tests.

- [ ] Write failing AutoMod alert/no-message-persistence test.
- [ ] Implement alert record/query.
- [ ] Run targeted and full backend tests.

### Task 4: Check/Report

**Files:**
- Create: `docs/03-analysis/T21-audit-security-actions-expansion.analysis.md`
- Create: `docs/04-report/T21-audit-security-actions-expansion.report.md`
- Modify: `.bkit-memory.json`

- [ ] Record evidence and residual risks.
- [ ] Commit report.
