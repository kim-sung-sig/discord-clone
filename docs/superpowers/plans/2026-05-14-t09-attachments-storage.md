# T09 Attachments/Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add validated attachment upload/download skeletons, metadata lifecycle, orphan cleanup, and Nuxt image preview/send UI.

**Architecture:** Add `backend:modules:storage` with a replaceable object-store port and in-memory adapter, expose REST endpoints in boot, and extend the Nuxt composer with attachment preview state. Object keys are server-generated and scoped.

**Tech Stack:** Java 21, Spring Boot MockMvc, JUnit 5, AssertJ, Nuxt 4, Pinia, Vitest, Playwright.

---

### Task 1: Backend Storage Domain

**Files:**
- Create: `backend/modules/storage/build.gradle.kts`
- Create: `backend/modules/storage/src/main/java/com/example/discord/storage/*.java`
- Test: `backend/modules/storage/src/test/java/com/example/discord/storage/InMemoryAttachmentServiceTest.java`
- Modify: `settings.gradle.kts`

- [ ] Write failing unit tests for size/type validation, server-generated key isolation, and orphan cleanup.
- [ ] Run `./gradlew.bat :backend:modules:storage:test` and verify RED.
- [ ] Implement metadata, policy, object store, presigned upload/download, cleanup.
- [ ] Run targeted module test and verify GREEN.
- [ ] Commit `feat: add storage domain module`.

### Task 2: Backend Attachment REST Adapter

**Files:**
- Create: `backend/boot/src/main/java/com/example/discord/storage/StorageConfiguration.java`
- Create: `backend/boot/src/main/java/com/example/discord/storage/AttachmentController.java`
- Test: `backend/boot/src/test/java/com/example/discord/storage/AttachmentControllerTest.java`
- Modify: `backend/boot/build.gradle.kts`

- [ ] Write failing MockMvc tests for upload request, invalid type, download isolation, and orphan cleanup.
- [ ] Run targeted boot test and verify RED.
- [ ] Implement controller/configuration.
- [ ] Run targeted boot test and verify GREEN.
- [ ] Commit `feat: expose attachment storage api`.

### Task 3: Nuxt Attachment Composer

**Files:**
- Create: `apps/web/components/shell/AttachmentPreview.vue`
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/components/shell/ChatViewport.vue`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`

- [ ] Write failing component tests for staged image preview and send-clears-staged attachment.
- [ ] Write failing Playwright attachment send smoke.
- [ ] Implement composer attachment state and preview.
- [ ] Run targeted Vitest/E2E and verify GREEN.
- [ ] Commit `feat: add attachment composer preview`.

### Task 4: T09 Verification and PDCA

**Files:**
- Create: `docs/03-analysis/T09-attachments-storage.analysis.md`
- Create: `docs/04-report/T09-attachments-storage.report.md`
- Create: `docs/05-feedback/T09-attachments-storage.feedback.md`
- Modify: `.bkit-memory.json`

- [ ] Run full gates and document evidence.
- [ ] Commit `docs: record T09 attachments storage PDCA`.
