# T150 Production Profile Guard Smoke Test Report

Date: 2026-05-20
Slice: T150 Production Profile Guard Smoke Test

## Completed

- Added `qa/production-profile-guard-smoke.ps1`.
- Added `qa/production-profile-guard-smoke.contract.ps1`.
- Added `qa-production-profile-guard` to `.github/workflows/ci.yml`.
- Updated `qa/ci-workflow.contract.ps1`.
- Added `RuntimeResourceProfileEnvironmentPostProcessor`.
- Registered the environment post processor in `backend/boot/src/main/resources/META-INF/spring.factories`.
- Extended `RuntimeResourceProfileGuardTest` to cover the early startup hook and registration.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/production-profile-guard-smoke.contract.ps1` failed because the smoke script did not exist.
  - `powershell -ExecutionPolicy Bypass -File qa/production-profile-guard-smoke.ps1` then failed because the component guard was too late and Spring failed on missing `AuthStore` first.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/production-profile-guard-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/production-profile-guard-smoke.ps1` passed and emitted `PRODUCTION_PROFILE_GUARD_SMOKE_PASS`.
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.RuntimeResourceProfileGuardTest --rerun-tasks` passed.
  - `.\gradlew.bat test` passed.
  - `git diff --check` passed for the T150 touched files, with only LF-to-CRLF working-copy warnings.

## Notes

- The smoke stores `bootrun.log` under `qa/artifacts/production-profile-guard`.
- No Postgres service or production secrets are provided to the CI job by design.
