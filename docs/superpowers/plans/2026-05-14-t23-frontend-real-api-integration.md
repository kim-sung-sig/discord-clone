# T23 Frontend Real API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a clearly separated real-backend frontend flow for login, guild/channel creation, message send, voice join, and stage start.

**Architecture:** Keep the existing deterministic Pinia shell for local tests. Add runtime-configured REST client access, memory-only auth login, and narrow real-backend shell actions that mutate UI only after successful API responses.

**Tech Stack:** Nuxt 4, Vue 3, Pinia, Vitest, Playwright, existing `createDiscordRestClient`, Spring Boot API.

---

### Task 1: Runtime REST Client And Auth Login

**Files:**
- Modify: `apps/web/nuxt.config.ts`
- Modify: `apps/web/stores/auth.ts`
- Modify: `apps/web/components/auth/LoginForm.vue`
- Modify: `apps/web/tests/components/login-form.test.ts`

- [ ] **Step 1: Write failing auth tests**

Add tests proving login calls `/api/auth/login`, stores token only in Pinia memory, and renders backend errors through `auth.error`.

- [ ] **Step 2: Run red test**

Run: `npm run test -- --run tests/components/login-form.test.ts` from `apps/web`.

Expected: FAIL because current auth store still accepts only `correct-password` locally.

- [ ] **Step 3: Implement runtime REST login**

Add `runtimeConfig.public.apiBaseUrl`, call `createDiscordRestClient`, store `accessToken` and `user` in Pinia state, and update login copy from local placeholder to backend login.

- [ ] **Step 4: Run green tests**

Run: `npm run test -- --run tests/components/login-form.test.ts tests/components/shell-contracts.test.ts` from `apps/web`.

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: connect login to backend api`.

### Task 2: Shell Real-Backend Actions

**Files:**
- Modify: `apps/web/stores/shell.ts`
- Modify: `apps/web/pages/app.vue`
- Modify: `apps/web/tests/components/app-shell.test.ts`

- [ ] **Step 1: Write failing shell tests**

Add tests proving `createBackendGuild`, `createBackendChannel`, `sendBackendMessage`, `joinBackendVoice`, and `startBackendStage` call REST paths and update shell state only after success.

- [ ] **Step 2: Run red test**

Run: `npm run test -- --run tests/components/app-shell.test.ts`.

Expected: FAIL because these real-backend actions do not exist.

- [ ] **Step 3: Implement real-backend actions**

Add `apiError`, `apiBusy`, and the five real-backend actions. Use bearer token from caller, map backend response fields into existing shell state shape, and keep existing local actions unchanged.

- [ ] **Step 4: Render accessible API errors**

Add a `role="alert"` shell API error region in `pages/app.vue` with `data-testid="shell-api-error"`.

- [ ] **Step 5: Run green tests**

Run: `npm run test -- --run tests/components/app-shell.test.ts tests/components/shell-contracts.test.ts`.

Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: add frontend real backend shell actions`.

### Task 3: Real-Backend Playwright Smoke

**Files:**
- Create: `apps/web/tests/e2e/real-backend.spec.ts`
- Modify: `apps/web/playwright.config.ts`

- [ ] **Step 1: Write real-backend Playwright spec**

Create a separate spec that creates a unique backend user via API fixture, logs in through `/login`, opens `/`, invokes the real-backend shell flow, and asserts UI text created from backend responses.

- [ ] **Step 2: Add environment guard**

Skip this spec unless `REAL_BACKEND_E2E=1` is set. Use `REAL_BACKEND_BASE_URL` or default `http://127.0.0.1:8080`.

- [ ] **Step 3: Run local e2e**

Run: `npm run e2e -- tests/e2e/app-shell.spec.ts tests/e2e/login.spec.ts`.

Expected: PASS and does not require backend.

- [ ] **Step 4: Run real-backend e2e**

Start Spring Boot with `SPRING_PROFILES_ACTIVE=postgres`, then run:

```powershell
$env:REAL_BACKEND_E2E='1'
$env:REAL_BACKEND_BASE_URL='http://127.0.0.1:8080'
npm run e2e -- tests/e2e/real-backend.spec.ts
```

Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `test: add frontend real backend smoke`.

### Task 4: T23 QA And PDCA Report

**Files:**
- Create: `docs/03-analysis/T23-frontend-real-api-integration.analysis.md`
- Create: `docs/04-report/T23-frontend-real-api-integration.report.md`
- Modify: `.bkit-memory.json`

- [ ] **Step 1: Run verification**

Run:

```powershell
.\gradlew.bat test
cd apps/web
npm run test -- --run
npm run build
npm run e2e -- tests/e2e/app-shell.spec.ts tests/e2e/login.spec.ts
```

- [ ] **Step 2: Run real-backend verification**

Run Spring Boot with `postgres` profile and execute `real-backend.spec.ts` with `REAL_BACKEND_E2E=1`.

- [ ] **Step 3: Write PDCA documents**

Record exact commands, pass/fail result, residual risks, and next recommended task.

- [ ] **Step 4: Commit**

Commit message: `docs: record T23 frontend real api PDCA`.
