# T134 Duplicate-Safe Grant Audit Result Report

Date: 2026-05-21
Slice: T134 Duplicate-Safe Grant Audit Result

## Completed

- Changed `AuthStore.grantGlobalRole` to return whether a role was newly inserted.
- Updated in-memory and JDBC stores to return duplicate-safe grant results.
- Updated the admin CLI runner to audit duplicate grants as `NOOP`.
- Updated the global admin runbook with duplicate grant expectations.

## Verification

- RED observed first:
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.GlobalAdminRoleCommandRunnerTest.recordsNoopAuditResultForDuplicateGrant --tests com.example.discord.auth.PostgresAuthStoreTest.storesGlobalRolesInPostgresWithoutDuplicates` failed at compile time because `grantGlobalRole` was still `void`.
- GREEN after implementation:
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.GlobalAdminRoleCommandRunnerTest.recordsNoopAuditResultForDuplicateGrant --tests com.example.discord.auth.PostgresAuthStoreTest.storesGlobalRolesInPostgresWithoutDuplicates` passed.
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.GlobalAdminRoleCommandRunnerTest --tests com.example.discord.auth.PostgresAuthStoreTest --tests com.example.discord.auth.AuthControllerTest` passed.
  - `.\gradlew.bat :backend:boot:checkstyleMain :backend:boot:checkstyleTest` passed.
  - `git diff --check` passed with CRLF warnings only.

## Notes

- Revoke `NOOP` behavior was already present and unchanged.
