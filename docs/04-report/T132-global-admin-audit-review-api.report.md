---
slug: T132-global-admin-audit-review-api
ticket: T132
phase: report
hub: "[[T132-global-admin-audit-review-api]]"
---
> 🧭 [[T132-global-admin-audit-review-api]] · PDCA: [[T132-global-admin-audit-review-api.plan]] → [[T132-global-admin-audit-review-api.design]] → [[T132-global-admin-audit-review-api.analysis]] → **report** → [[T132-global-admin-audit-review-api.feedback]]

# T132 Global Admin Audit Review API Report

Date: 2026-05-20
Slice: T132 Global Admin Audit Review API

## Completed

- Added `GET /api/admin/global-roles/audit-log`.
- Added security-admin authorization in `AuthService`.
- Added response DTOs in `AuthController`.
- Added bounded latest-first audit review methods to `AuthStore`, `InMemoryAuthStore`, and `JdbcAuthStore`.
- Added controller tests for allowed security admin access and forbidden non-admin access.
- Added OpenAPI generator coverage and regenerated API artifacts.

## Verification

- RED observed first:
  - Focused `AuthControllerTest` audit review tests failed because the endpoint did not exist.
- GREEN after implementation:
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthControllerTest.securityAdminCanReviewGlobalRoleAuditEntries --tests com.example.discord.auth.AuthControllerTest.rejectsNonSecurityAdminGlobalRoleAuditReview` passed.
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthControllerTest --tests com.example.discord.auth.GlobalAdminRoleCommandRunnerTest --tests com.example.discord.auth.PostgresAuthStoreTest` passed.
  - `npm run openapi:check` passed.
  - `.\gradlew.bat test` passed.

## Notes

- A parallel Gradle attempt briefly failed due a locked test result file; the affected Postgres store test passed when rerun alone.
