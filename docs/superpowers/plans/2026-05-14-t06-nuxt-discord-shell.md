# T06 Nuxt Discord Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden the Nuxt Discord shell with API/Gateway seams, story assets, accessibility checks, and visual smoke evidence.

**Architecture:** Frontend remains deterministic for tests but gains contract-level clients for REST/Gateway integration. Story files and visual smoke tests provide a UI regression baseline without adding network-dependent Storybook packages yet.

**Tech Stack:** Nuxt 3, Pinia, Vue 3, Vitest, Playwright, TypeScript.

---

## Task 1: API/Gateway Contract Seam

**Files:**
- Create: `apps/web/services/discord-api.ts`
- Create: `apps/web/services/gateway-client.ts`
- Modify: `apps/web/stores/shell.ts`
- Create: `apps/web/tests/components/shell-contracts.test.ts`
- Modify: `apps/web/tests/components/app-shell.test.ts`

- [ ] **Step 1: Write failing tests**

Cover REST endpoint builders, gateway event validation, store hydration action, and stale/duplicate sequence guard.

- [ ] **Step 2: Verify RED**

Run: `npm run test -w apps/web -- --run tests/components/shell-contracts.test.ts tests/components/app-shell.test.ts`

- [ ] **Step 3: Implement contract clients and store seam**

Add endpoint builders and typed actions without requiring live backend.

- [ ] **Step 4: Verify GREEN**

Run the same component tests.

## Task 2: Story Assets And Layout Accessibility

**Files:**
- Create: `apps/web/components/shell/*.stories.ts`
- Create: `apps/web/components/invite/InviteModal.stories.ts`
- Modify: `apps/web/assets/css/main.css`
- Modify: `apps/web/tests/components/app-shell.test.ts`
- Create: `apps/web/tests/components/story-index.test.ts`

- [ ] **Step 1: Write failing tests**

Assert required story metadata and keyboard/focus landmarks.

- [ ] **Step 2: Verify RED**

Run: `npm run test -w apps/web -- --run tests/components/story-index.test.ts tests/components/app-shell.test.ts`

- [ ] **Step 3: Implement stories and responsive/focus hardening**

Add story metadata and layout tweaks.

- [ ] **Step 4: Verify GREEN**

Run the same component tests.

## Task 3: Visual Smoke And PDCA Closure

**Files:**
- Modify: `apps/web/tests/e2e/app-shell.spec.ts`
- Create: `docs/03-analysis/T06-nuxt-discord-shell.analysis.md`
- Create: `docs/04-report/T06-nuxt-discord-shell.report.md`
- Create: `docs/05-feedback/T06-nuxt-discord-shell.feedback.md`

- [ ] **Step 1: Add failing visual smoke assertions**

Capture desktop/mobile screenshots and assert non-empty artifacts.

- [ ] **Step 2: Verify RED/GREEN**

Run: `npm run e2e -w apps/web -- app-shell.spec.ts`

- [ ] **Step 3: Run full gates**

Run backend tests, frontend component tests, e2e, build, and compose config.

- [ ] **Step 4: Review, fix, and commit**

Run reviews, close findings, and commit frontend plus PDCA docs separately.
