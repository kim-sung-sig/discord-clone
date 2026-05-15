# T23 Frontend Real API Integration Stabilization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Stabilize real-backend frontend actions with explicit request correlation and safe error handling.

**Architecture:** Pinia shell store owns action-level request ids and passes them into `createDiscordRestClient` request options.

**Tech Stack:** Nuxt 4, Pinia, Vitest, Playwright.

---

### Task 1: Planning
- [ ] Commit T23 plan/design docs.

### Task 2: Request Correlation and Error Policy
- [ ] Add failing component tests for request id headers and error request id display.
- [ ] Add `apiLastRequestId` state and pass request ids to REST calls.
- [ ] Run frontend unit tests.

### Task 3: Check/Report
- [ ] Run `npm run test -- --run`.
- [ ] Run `npm run build`.
- [ ] Create analysis/report docs.
