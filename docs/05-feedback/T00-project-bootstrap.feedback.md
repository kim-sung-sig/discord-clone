# T00 Project Bootstrap Feedback

작성일: 2026-05-13

## Feedback Log

### F-001: Nuxt test dependency missing

- Failure Summary: Vitest failed before loading the app test because `happy-dom` was missing.
- Reproduction: `npm run test -w apps/web -- --run tests/components/app-shell.test.ts`
- Expected: Test fails because `app.vue` does not exist.
- Actual: `@nuxt/test-utils` could not resolve `happy-dom`.
- Root Cause: Nuxt test utils require `happy-dom` for this environment.
- Fix Plan: Add `happy-dom` as a dev dependency.
- Re-test Evidence: After installing `happy-dom`, the test reached the intended missing `app.vue` failure.

### F-002: TypeScript config referenced generated Nuxt file too early

- Failure Summary: Vitest failed because `.nuxt/tsconfig.json` did not exist before Nuxt prepare.
- Reproduction: `npm run test -w apps/web -- --run tests/components/app-shell.test.ts`
- Expected: Test fails because `app.vue` does not exist.
- Actual: Vite failed to resolve `./.nuxt/tsconfig.json`.
- Root Cause: Bootstrap test harness should not require generated Nuxt files before first test run.
- Fix Plan: Use a standalone TypeScript config for initial tests.
- Re-test Evidence: After changing `tsconfig.json`, the test reached the intended missing `app.vue` failure.

### F-003: Playwright browser missing

- Failure Summary: Playwright failed because Chromium was not installed.
- Reproduction: `npm run e2e -w apps/web`
- Expected: Browser launches and checks app shell landmarks.
- Actual: `chrome-headless-shell.exe` did not exist.
- Root Cause: Playwright browser binaries are separate from npm package installation.
- Fix Plan: Run `npx playwright install chromium`.
- Re-test Evidence: After installing Chromium, e2e smoke passed.
