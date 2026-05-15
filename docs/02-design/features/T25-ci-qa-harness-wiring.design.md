# T25 CI QA Harness Wiring Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T25 CI QA Harness Wiring

## Architecture Decision

Use GitHub Actions on `ubuntu-latest` for CI parity with service containers. PowerShell Core (`pwsh`) remains the QA script runtime so local Windows usage and CI Linux usage share the same scripts.

## Workflow Jobs

- `backend`: setup Java 21, run `./gradlew test`.
- `frontend`: setup Node 22, run `npm ci`, `npm run test --workspace @discord-clone/web -- --run`, and `npm run build --workspace @discord-clone/web`.
- `qa-runtime`: provision PostgreSQL service, install Chromium dependencies, run `qa/real-backend-e2e.contract.ps1`, run `qa/real-backend-e2e.ps1`, upload `qa/artifacts/real-backend-e2e`.
- `qa-toolchain`: run `qa/toolchain-warning-scan.ps1`, upload `qa/artifacts/toolchain`.

## Script Portability

`qa/real-backend-e2e.ps1` and `qa/toolchain-warning-scan.ps1` should select `gradlew.bat` on Windows and `./gradlew` elsewhere. `qa/real-backend-e2e.ps1` should select `npm.cmd` on Windows and `npm` elsewhere, and use `-WindowStyle Hidden` only on Windows.

## Test Strategy

Add `qa/ci-workflow.contract.ps1` to validate that the workflow references required jobs, PostgreSQL service, PowerShell scripts, artifact upload, Java 21, Node 22, and Playwright browser setup. This does not replace GitHub execution; it prevents accidental workflow drift in local changes.

## Risks

- GitHub-hosted runner image changes can affect Playwright dependencies.
- Real-backend smoke is heavier than unit tests and may need timeout tuning.
- Workflow cannot be fully proven locally without a GitHub Actions runner; the contract test covers structure only.
