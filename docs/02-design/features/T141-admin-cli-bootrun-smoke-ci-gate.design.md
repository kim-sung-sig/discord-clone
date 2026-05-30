# T141 Admin CLI BootRun Smoke CI Gate Design

Date: 2026-05-20
PDCA Phase: Design
Slice: T141 Admin CLI BootRun Smoke CI Gate

## Design

Add a dedicated `qa-admin-cli` GitHub Actions job. The job sets up Java, runs the existing admin CLI smoke contract, runs the smoke, and uploads `qa/artifacts/admin-cli`.

## Smoke Script Changes

| Concern | Design |
| --- | --- |
| Platform | `Get-GradleCommand` selects `gradlew.bat` on Windows and `gradlew` elsewhere. |
| Postgres readiness | Reuse `postgres-source` when ready; otherwise run `docker compose -f infra/docker/docker-compose.yml up -d postgres-source`. |
| Fresh database | If required tables are missing, run an admin-cli warmup bootRun to trigger Flyway, then seed the smoke user. |
| Logs | Write `bootrun-migrate.log` and `bootrun-list.log` under `ADMIN_CLI_ARTIFACT_DIR`. |
| Secrets | Pass datasource values through `POSTGRES_JDBC_URL`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`, not through CLI args. |

## Security Review

- The smoke uses development database credentials only.
- The database password is no longer placed in the Gradle `--args` string.
- The command remains list-only, so it does not mutate global roles.
