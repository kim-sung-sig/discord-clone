# T01 Identity/User Feedback

작성일: 2026-05-13

## Feedback Log

### F-001: BCrypt runtime dependency missing

- Failure Summary: Identity tests failed at runtime with `NoClassDefFoundError: org/apache/commons/logging/LogFactory`.
- Reproduction: `.\gradlew.bat :backend:modules:identity:test`
- Expected: `BCryptPasswordHasher` hashes and verifies password.
- Actual: `BCryptPasswordEncoder` could not initialize.
- Root Cause: `spring-security-crypto` depends on Commons Logging classes at runtime in this module configuration.
- Fix Plan: Add `commons-logging:commons-logging:1.3.4` as a runtime dependency to `backend/modules/identity/build.gradle.kts`.
- Re-test Evidence: `.\gradlew.bat :backend:modules:identity:test` passed after adding the dependency.

### F-002: Backend review security findings

- Failure Summary: Review found duplicate signup overwrite, non-revoking logout, non-thread-safe lockout tracking, unknown-email enumeration difference, unmapped bad input, refresh rotation gaps, and hard-coded signing secret.
- Reproduction: Static backend review plus added regression tests in `AuthControllerTest` and `RefreshSessionTest`.
- Expected: Auth API should reject duplicate signup, revoke logged-out bearer token, lock repeated failures consistently, map bad requests to 400, and reject invalid refresh rotation.
- Actual: Initial worker implementation missed these behaviors.
- Root Cause: Minimal in-memory slice implemented only the first auth contract tests.
- Fix Plan: Add targeted tests and minimal implementation for each review item.
- Re-test Evidence: `.\gradlew.bat test --rerun-tasks` passed with 19 actionable tasks executed.

### F-003: Frontend review routing and state findings

- Failure Summary: Review found root app routing bypassed Nuxt 404s, login tests could share Pinia state, e2e did not verify memory-only token behavior, and hydration-disabled controls lacked an accessible explanation.
- Reproduction: Static frontend review plus updated e2e coverage for unknown route and token storage.
- Expected: Nuxt routing should handle pages/404 normally, tests should be isolated, token should not persist, and disabled controls should be explained.
- Actual: Initial frontend worker implementation used a route check in `app.vue` and lacked these assertions.
- Root Cause: Fast login slice optimized for preserving shell tests rather than Nuxt route correctness.
- Fix Plan: Always render `<NuxtPage />`, move shell to root page, reset Pinia in tests, add storage assertions, and add hydration status text.
- Re-test Evidence: frontend component tests, Playwright e2e, and Nuxt production build passed.
