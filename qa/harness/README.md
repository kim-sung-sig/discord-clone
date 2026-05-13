# T00 QA Harness

This harness defines the minimum verification gate for the Discord clone bootstrap.

## Backend

```powershell
.\gradlew.bat test
```

Expected result:

- All JUnit tests pass.
- Permission bitset tests pass.
- Identifier tests pass.
- Spring context smoke test passes.
- ArchUnit baseline test passes.

## Frontend Component Tests

```powershell
npm run test -w apps/web -- --run tests/components/app-shell.test.ts
```

Expected result:

- Vitest reports 1 file passed and 1 test passed.

## Frontend E2E Smoke

```powershell
npm run e2e -w apps/web
```

Expected result:

- Playwright reports 1 Chromium test passed.
- The app shell exposes `server-rail`, `channel-sidebar`, `chat-viewport`, `member-sidebar`, and `user-panel`.

## Local Infrastructure Config

```powershell
docker compose -f infra/docker/docker-compose.yml config
```

Expected result:

- Docker Compose renders a valid config for PostgreSQL, Redis, Redpanda, and MinIO.

## Failure Handling

If any command fails:

1. Record the failure in `docs/05-feedback/T00-project-bootstrap.feedback.md`.
2. Fix the smallest reproducible issue.
3. Re-run the failed command.
4. Run the full gate before marking T00 complete.
