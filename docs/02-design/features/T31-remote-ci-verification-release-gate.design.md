# T31 Remote CI Verification Release Gate Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T31 Remote CI Verification & Release Gate

## Architecture Decision

The release gate should validate the repository at the workspace boundary, not only the current Nuxt app boundary. The frontend CI job will use `npm test --workspaces` so web, desktop shell, platform shell, and UI contract packages are checked by the same remote runner before merge.

## Workflow Design

| Job | Purpose | Required Evidence |
| --- | --- | --- |
| `backend` | Spring Boot unit and slice tests | `./gradlew test` exits 0 |
| `frontend` | All npm workspace contract/component tests plus Nuxt build | `npm test --workspaces` and web build exit 0 |
| `qa-runtime` | Real backend smoke with PostgreSQL service and Playwright | API/browser smoke logs and uploaded artifacts |
| `qa-toolchain` | Warning budget and toolchain drift scan | warning scan logs and uploaded artifacts |

## Contract Design

`qa/ci-workflow.contract.ps1` remains a fast local static guard. It must assert that:

- CI has push, PR, and manual triggers.
- Required jobs are present.
- Java 21 and Node 22 are used.
- PostgreSQL CI service uses the agreed dev credentials.
- Playwright Chromium is installed for runtime QA.
- Real-backend and toolchain artifacts are uploaded.
- Frontend tests run through `npm test --workspaces`.

## Remote Verification Procedure

1. Run local workflow contract after CI edits.
2. Run local workspace tests to confirm the new frontend command is valid.
3. Commit the T31 gate changes.
4. Push `feature/t02-guild-channel-permission` to `origin`.
5. Use GitHub Actions run metadata to identify the CI run for the pushed commit.
6. Watch the run until terminal state.
7. If any job fails, capture failed logs and artifacts, create T31 feedback notes, patch the defect, and push again.

## Risk Controls

- Use one branch push as the remote trigger so the CI result maps to a known commit SHA.
- Keep artifact upload steps under `if: always()` so failures still provide diagnostic evidence.
- Treat GitHub authentication failure as an external blocker, not a code success.
- Do not mark T31 complete until fresh local and remote evidence are documented.
