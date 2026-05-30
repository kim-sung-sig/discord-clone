# T118 Global Admin Grant Operations Tool Report

## Completed

- Added `application-admin-cli.yml`.
- Added `GlobalAdminRoleCommandRunner`.
- Added `AuthStore.revokeGlobalRole`.
- Implemented revoke in in-memory and JDBC stores.
- Added CLI behavior tests.
- Extended Postgres auth store test for duplicate-safe revoke.

## Verification

- RED observed first:
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.auth.GlobalAdminRoleCommandRunnerTest --tests com.example.discord.auth.PostgresAuthStoreTest` failed because `GlobalAdminRoleCommandRunner` and `AuthStore.revokeGlobalRole` did not exist.
- GREEN after implementation:
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.auth.GlobalAdminRoleCommandRunnerTest --tests com.example.discord.auth.PostgresAuthStoreTest` passed.
  - `./gradlew.bat :backend:boot:test` passed.
  - `git diff --check` passed for T118-touched files.

## Notes

- Gradle still reports existing deprecation warnings for Gradle 9 compatibility.

