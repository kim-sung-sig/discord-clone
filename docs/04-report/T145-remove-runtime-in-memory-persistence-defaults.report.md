# T145 Remove Runtime In-Memory Persistence Defaults Report

Date: 2026-05-20
Slice: T145 Remove Runtime In-Memory Persistence Defaults

## Completed

- Added production-like runtime profile guard.
- Required `postgres` for `production` and `admin-cli`.
- Blocked in-memory auth/guild/message/invite persistence beans from production-like profiles.
- Preserved local and test in-memory behavior.

## Verification

- RED observed first:
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.ops.RuntimeResourceProfileGuardTest` failed because `RuntimeResourceProfileGuard` did not exist.
- GREEN after implementation:
  - `./gradlew.bat :backend:boot:test --tests com.example.discord.ops.RuntimeResourceProfileGuardTest` passed.
  - `./gradlew.bat :backend:boot:test` passed.
  - `git diff --check` passed for T145-touched files.

## Notes

- Gradle still emits the known deprecation warning and JVM class sharing warning; tests exit successfully.
