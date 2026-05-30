# T157 Central Redis CI Failure Artifacts Design

Date: 2026-05-20
Slice: T157 Central Redis CI Failure Artifacts

## Design

`qa-central-redis` now writes and uploads artifacts under `qa/artifacts/central-redis`.

## Smoke Output

`qa/central-redis-smoke.ps1` accepts `CENTRAL_REDIS_ARTIFACT_DIR`.

- If unset, it defaults to `qa/artifacts/central-redis/local`.
- If relative, it is resolved from the repository root.
- Vitest runs with `--reporter=junit` and writes `vitest-junit.xml`.

## Artifact Collection

`qa/central-redis-ci-artifacts.ps1` collects:

- `docker ps -a`
- `docker compose ps`
- `docker compose config`
- Compose Redis logs
- Standalone `ms-redis` logs when available
- Gradle HTML test report
- Gradle XML test results
- Vitest test result directory when available

## CI Wiring

The artifact collection step runs with `if: failure()` after the Redis smoke step. Upload also runs with `if: failure()` and uses `actions/upload-artifact@v4`.
