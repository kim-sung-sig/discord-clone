# T111 Backend Global Admin Role Contract Report

## Completed

- Added migration `V7__user_global_roles.sql`.
- Extended `AuthStore` with global role grant/read methods.
- Implemented global roles in `InMemoryAuthStore` and `JdbcAuthStore`.
- Added `SECURITY_ADMIN` role mapping to `admin: true`.
- Extended auth user responses with `roles` and `admin`.
- Added controller and Postgres store tests.

## Verification

- RED observed first:
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthControllerTest --tests com.example.discord.auth.PostgresAuthStoreTest` failed because `AuthStore.grantGlobalRole` and `globalRolesForUser` did not exist.
- GREEN after implementation:
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthControllerTest --tests com.example.discord.auth.PostgresAuthStoreTest` passed.
  - `./gradlew.bat :backend:boot:test` passed.
  - `npm test -w apps/web -- security-dashboard-access.test.ts` passed.
  - `npm test -w apps/web` passed with 9 files and 77 tests.
  - `git diff --check` passed for T111-touched files.

## Notes

- Gradle still reports existing deprecation warnings for Gradle 9 compatibility.
- Web tests still emit the known Node experimental SQLite warning from T106.

