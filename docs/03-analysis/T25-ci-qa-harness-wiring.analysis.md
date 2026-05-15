# T25 CI QA Harness Wiring Analysis

작성일: 2026-05-15  
PDCA Phase: Check  
Slice: T25 CI QA Harness Wiring

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` | PASS | `CI_WORKFLOW_CONTRACT_PASS` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.contract.ps1` | PASS | `REAL_BACKEND_E2E_CONTRACT_PASS` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/real-backend-e2e.ps1 -PostgresJdbcUrl 'jdbc:postgresql://127.0.0.1:15432/discord'` | PASS | API smoke PASS and real-backend Playwright 1 test passed; latest artifact `qa/artifacts/real-backend-e2e/20260515-181526/` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File qa/toolchain-warning-scan.ps1` | PASS | Gradle test and Nuxt build completed; logs under `qa/artifacts/toolchain/` |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| CI workflow includes backend/frontend/toolchain/real-backend jobs | PASS | `.github/workflows/ci.yml` defines `backend`, `frontend`, `qa-runtime`, and `qa-toolchain` |
| Runtime job provisions PostgreSQL service | PASS | workflow service uses `postgres:17`, `discord`, `dev_user`, and `dev_password` |
| QA artifacts are uploaded | PASS | workflow uploads `qa/artifacts/real-backend-e2e` and `qa/artifacts/toolchain` with `if: always()` |
| QA scripts are cross-platform | PASS | scripts select Gradle/npm commands by OS and avoid Windows-only `Start-Process` options on Linux |
| Workflow structure is covered locally | PASS | `qa/ci-workflow.contract.ps1` validates required CI wiring |

## Failure Analysis

| Failure | Root Cause | Fix |
| --- | --- | --- |
| `real-backend-e2e.ps1` failed to start backend after portability change | Windows PowerShell 5 does not define `$IsWindows`, causing the script to select `gradlew` instead of `gradlew.bat` | Added `Test-IsWindows` helper based on `System.Environment.OSVersion.Platform` |
| `toolchain-warning-scan.ps1` failed on Nuxt stderr warning output | `$ErrorActionPreference='Stop'` promoted native stderr warning records to terminating errors | Native commands now capture output while judging success by exit code |

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| GitHub Actions workflow was not executed remotely in this local session | Medium | Verify first pushed run and address runner-specific issues if any appear |
| Workflow uses dev database password in CI YAML | Low | Acceptable for ephemeral local/CI test DB; production secrets are out of scope |
| Toolchain scan still records known Nuxt/Vue warnings | Low | Tracked by T22 warning budget |

## Decision

T25 is acceptable for current roadmap scope. The local QA harnesses are now wired into a GitHub Actions workflow with artifact upload, and the scripts are portable enough for Ubuntu CI while still passing on the current Windows workstation.
