# T121 Admin CLI BootRun Smoke Test Analysis

Date: 2026-05-19
Slice: T121 Admin CLI BootRun Smoke Test

## Analysis

Direct unit tests proved command behavior, but they did not prove the `admin-cli` Spring profile could instantiate the command runner through the real application context. The first Docker-backed smoke reproduced a runtime failure: Spring saw multiple constructors on `GlobalAdminRoleCommandRunner` and attempted default construction.

The root cause was constructor selection, not Postgres, Flyway, or Gradle argument handling. Adding `@Autowired` to the runtime constructor keeps the existing test-only constructors while making the Spring injection path explicit.

## Trade-Offs

- The smoke uses PowerShell because the project QA scripts already run on the current Windows desktop workflow.
- It seeds deterministic data in the shared central `discord` database instead of managing a temporary database.
- It verifies the non-mutating `list` command first. Mutating grant/revoke smoke checks are intentionally deferred.

## Security Notes

- The script uses local development credentials already designated for the central Docker Postgres source.
- The smoke prints only the deterministic smoke user id and role list output.
- It does not expose passwords in generated reports beyond the development connection values already used by local environment examples.

## Residual Risk

The central smoke database can accumulate deterministic test data. CI gating and isolated database cleanup are registered as follow-up tasks.
