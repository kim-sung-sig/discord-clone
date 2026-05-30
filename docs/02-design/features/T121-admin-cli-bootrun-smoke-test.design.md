# T121 Admin CLI BootRun Smoke Test Design

Date: 2026-05-19
Slice: T121 Admin CLI BootRun Smoke Test

## Architecture

The smoke test is a repository-level QA script under `qa/`. It exercises the production-like CLI launch path through Gradle `bootRun` instead of directly instantiating the command runner.

## Smoke Flow

1. Confirm `application-admin-cli.yml` keeps `spring.main.web-application-type: none`.
2. Confirm Docker container `postgres-source` is running.
3. Create the `discord` database if it is missing.
4. Confirm required tables are migrated: `users`, `auth_accounts`, and `user_global_roles`.
5. Seed a deterministic smoke user and auth account.
6. Run:

```powershell
.\gradlew.bat :backend:boot:bootRun --args="--spring.profiles.active=admin-cli,postgres ..."
```

7. Assert the CLI prints `global roles for <smoke-user-id>`.
8. Emit `ADMIN_CLI_BOOTRUN_SMOKE_PASS`.

## Constructor Injection Fix

`GlobalAdminRoleCommandRunner` has convenience constructors for unit tests and one constructor for Spring Boot runtime dependencies. Because multiple constructors exist, the runtime constructor must be explicitly marked with `@Autowired`.

## Test Strategy

- `qa/admin-cli-bootrun-smoke.contract.ps1` verifies the QA script contract and key operational arguments.
- `GlobalAdminRoleCommandRunnerTest` verifies the intended Spring constructor is marked for injection.
- `qa/admin-cli-bootrun-smoke.ps1` verifies the full launch path against central Postgres.

## Operational Notes

The smoke currently uses the shared `discord` database. Follow-up work should isolate smoke data or run against a temporary database to avoid operator confusion.
