# T03 Invite Analysis

작성일: 2026-05-13  
PDCA Phase: Check  
Slice: T03 invite

## Verification Matrix

| Gate | Command | Status | Evidence |
| --- | --- | --- | --- |
| Backend targeted RED/GREEN | `.\gradlew.bat :backend:modules:invite:test :backend:boot:test --tests com.example.discord.invite.InviteControllerTest --tests com.example.discord.architecture.ArchitectureTest` | Pass | Worker reported `BUILD SUCCESSFUL`. |
| Backend fresh full gate | `.\gradlew.bat test --rerun-tasks` | Pass | `BUILD SUCCESSFUL in 24s`; 29 actionable tasks executed. |
| Frontend component gate | `npm run test -w apps/web -- --run tests/components/login-form.test.ts tests/components/app-shell.test.ts` | Pass | 2 test files passed, 6 tests passed. |
| Frontend e2e gate | `npm run e2e -w apps/web` | Pass | 4 Playwright tests passed. |
| Frontend build gate | `npm run build -w apps/web` | Pass | Nuxt production build completed with known upstream sourcemap/deprecation warnings. |
| Docker compose config | `docker compose -f infra/docker/docker-compose.yml config` | Pass | Compose rendered `name: discord-clone`. |

## RED Evidence

- Backend invite tests failed before invite module and REST adapter existed.
- Frontend shell component test failed on missing `invite-modal`.

## Implemented Scope

- Added invite backend module.
- Added invite create/delete/preview/accept REST API.
- Added max age, max uses, delete, idempotent same-member accept, temporary membership, and role grant skeleton behavior.
- Added service race safety test for max uses.
- Added Nuxt invite modal with preview metadata, max-use/expiry text, role grant skeleton, and accept CTA.

## Gap List

- Invite persistence remains in-memory.
- Frontend invite modal uses static seed state and is not API-connected.
- Audit log and delivery/deep-link flows are not implemented.
- Nuxt build still reports upstream sourcemap/deprecation warnings.

