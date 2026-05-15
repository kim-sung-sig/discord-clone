# T24 Real Backend QA Orchestration Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T24 Real Backend QA Orchestration

## Architecture Decision

Add a thin PowerShell orchestrator in `qa/real-backend-e2e.ps1`. It should not duplicate API assertions or Playwright logic; instead it composes the existing backend service, `qa/api-smoke.ps1`, and `apps/web/tests/e2e/real-backend.spec.ts`.

## Execution Model

1. Normalize artifact directory and log paths.
2. Probe backend health at `/actuator/health`.
3. If backend is unavailable and `-SkipServiceStart` is not set, start `./gradlew.bat :backend:boot:bootRun` with PostgreSQL env overrides.
4. Wait until backend health responds or fail with the backend log path.
5. Run `qa/api-smoke.ps1 -BaseUrl <BackendUrl>` and tee output to artifacts.
6. Run `npm run e2e -- tests/e2e/real-backend.spec.ts` in `apps/web` with `REAL_BACKEND_E2E=1`, `REAL_BACKEND_BASE_URL`, and `NUXT_PUBLIC_API_BASE_URL`.
7. Stop only the backend process started by the harness.

## Database Policy

The harness defaults to the user's current local Docker convention: `127.0.0.1:5432`, database `discord`, user `dev_user`, password `dev_password`. It exposes parameters to override these values without modifying Spring config.

## Error Handling

Every external command must fail fast and point to the artifact log. Backend startup timeout must include the expected health URL and the backend log file. The harness must not kill a backend it did not start.

## Test Strategy

Add `qa/real-backend-e2e.contract.ps1` to statically validate required parameters, env wiring, service-start switch, and Playwright command references. Runtime service execution remains manual/environment-gated because it needs PostgreSQL and open local ports.

## Risks

- Local port collisions can still block startup; the artifact log is the primary diagnostic.
- Database creation is not guaranteed unless the local PostgreSQL user has an existing `discord` database or createdb privileges.
- Playwright browser dependencies must already be installed in the frontend workspace.
