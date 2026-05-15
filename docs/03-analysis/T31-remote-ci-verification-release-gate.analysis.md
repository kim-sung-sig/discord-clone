# T31 Remote CI Verification Release Gate Analysis

작성일: 2026-05-15  
PDCA Phase: Check  
Slice: T31 Remote CI Verification & Release Gate

## Verification Evidence

| Command / Evidence | Result | Notes |
| --- | --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` | PASS | `CI_WORKFLOW_CONTRACT_PASS` |
| `npm test --workspaces` | PASS | web 42, desktop 4, platform-shell 2, ui-contracts 4 tests passed |
| `npm run build --workspace @discord-clone/web` | PASS | Nuxt production build completed with known warning output |
| `.\gradlew.bat clean test` | PASS | 92 Gradle tasks, backend and module tests completed |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.ps1 -PostgresJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord' -SkipServiceStart` | PASS | API smoke and real-backend Playwright 1 test passed |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/toolchain-warning-scan.ps1` | PASS | Gradle warning scan and Nuxt build completed |
| GitHub Actions Run 1 `25922823616` | FAIL | frontend passed; backend, qa-runtime, qa-toolchain failed |
| GitHub Actions Run 2 `25923439279` | FAIL | backend, frontend, qa-toolchain passed; qa-runtime failed |
| GitHub Actions Run 3 `25923692040` | FAIL | qa-runtime failed before full artifact logs were useful |
| GitHub Actions Run 4 `25923907634` | PASS | Actions list reported `completed successfully: Run 4 of ci` for commit `2c85e08` |

## Remote Failure Feedback

| Feedback | Root Cause | Fix |
| --- | --- | --- |
| Remote `backend` job failed while local tests passed | Postgres-profile Spring tests require a database; the backend CI job did not provision PostgreSQL | Added PostgreSQL service and `POSTGRES_JDBC_URL` env to backend job |
| Remote `qa-toolchain` failed | Warning scan runs Gradle tests, which also need PostgreSQL for postgres-profile tests | Added PostgreSQL service and env to qa-toolchain job |
| Remote `qa-runtime` failed early | PowerShell-managed background backend startup was not reliable on Ubuntu runner and produced only minimal metadata artifact | Split backend startup into a bash CI step and run harness with `-SkipServiceStart` |
| Runtime artifact was too small to diagnose first failure | Harness could fail before backend logs were created | Added metadata file and moved CI backend logs into `qa/artifacts/real-backend-e2e/ci` |
| GitHub API logs/artifact download blocked | Unauthenticated API access cannot download logs/artifact zip and later hit rate limits | Used public Actions HTML/partials for run/job/annotation status; documented auth limitation |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| CI workflow contract passes locally | PASS | `CI_WORKFLOW_CONTRACT_PASS` |
| Frontend job runs full workspace tests | PASS | `.github/workflows/ci.yml` uses `npm test --workspaces`; Run 4 completed successfully |
| Active branch pushed to origin | PASS | Branch tracks `origin/feature/t02-guild-channel-permission` |
| Remote CI run executed for pushed branch | PASS | Runs 1-4 executed on GitHub Actions |
| Backend/frontend/qa-runtime/qa-toolchain results recorded | PASS | Run 4 success recorded; intermediate failures documented |
| Remote-only failures have fixes | PASS | Fix commits `16b9ba1`, `91ec422`, `2c85e08` |

## Residual Risks

| Risk | Impact | Follow-up |
| --- | --- | --- |
| GitHub detailed logs require authenticated access | Medium | Install/authenticate `gh` or provide GitHub token for future faster CI triage |
| GitHub Actions Node.js 20 action runtime deprecation warning | Low now, medium by 2026-06-02 | Track action version updates or set runner opt-in after validating ecosystem support |
| Nuxt/Vue known warning output remains | Low | Covered by existing warning-budget follow-up |

## Decision

T31 is accepted. The release gate now verifies backend, frontend workspace tests, real-backend runtime QA, and toolchain warning scan on GitHub-hosted runners. The final observed remote run for commit `2c85e08` completed successfully.
