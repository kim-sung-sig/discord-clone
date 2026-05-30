# T121 Admin CLI BootRun Smoke Test Plan

Date: 2026-05-19
Slice: T121 Admin CLI BootRun Smoke Test

## Loop Output

Plan reviewed > implementation plan prepared > implementation in progress > review in progress > review complete > threshold decision > next plan or improvement loop

## Plan Review

T118 delivered the `admin-cli` grant, revoke, and list command runner. T121 verifies the actual Spring Boot profile path, not only direct unit calls, so operators can trust the CLI profile to start with `web-application-type=none`, connect to centralized Postgres, execute one command, and terminate cleanly.

## Implementation Plan

Major topics:

1. Smoke contract
   - Add a QA contract script that confirms the smoke script exists, parses, targets `:backend:boot:bootRun`, uses `admin-cli,postgres`, and checks the expected PASS marker.

2. BootRun smoke
   - Seed a deterministic smoke user in the centralized `postgres-source` container.
   - Run Gradle `:backend:boot:bootRun` with `admin-cli,postgres` and the central Postgres connection.
   - Verify list-command output and process exit code.

3. Spring constructor compatibility
   - Reproduce the actual Spring runner instantiation issue.
   - Mark the Spring Boot runner constructor as the intended injection constructor.

4. Documentation and queue updates
   - Record RED/GREEN evidence, risks, and follow-up tasks.
   - Move T121 to completed and set T122 as the next recommended task.

## Out of Scope

- Full CI integration of the smoke command.
- Grant/revoke bootRun smoke coverage.
- Dedicated temporary database lifecycle automation.

## Acceptance Criteria

- Contract script passes.
- Actual admin CLI smoke prints `ADMIN_CLI_BOOTRUN_SMOKE_PASS`.
- Focused runner test passes.
- The smoke uses `admin-cli,postgres` and central Postgres on port `15432`.
- T121 documents and residual task queue are updated.
