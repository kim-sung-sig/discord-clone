# T121 Admin CLI BootRun Smoke Test Report

Date: 2026-05-19
Slice: T121 Admin CLI BootRun Smoke Test

## Summary

T121 added a PowerShell contract and Docker-backed smoke test for the actual `admin-cli,postgres` Gradle `bootRun` path. The smoke caught and fixed a real Spring constructor injection issue in `GlobalAdminRoleCommandRunner`.

## Loop Result

Plan reviewed > implementation plan prepared > implementation completed > review completed > 28/30 PASS > next plan can proceed

## Implemented Changes

- Added `qa/admin-cli-bootrun-smoke.contract.ps1`.
- Added `qa/admin-cli-bootrun-smoke.ps1`.
- Added a constructor-injection contract test to `GlobalAdminRoleCommandRunnerTest`.
- Marked the Spring runtime constructor in `GlobalAdminRoleCommandRunner` with `@Autowired`.
- Updated the residual task queue and captured follow-up improvements.

## Verification

Initial RED:

```powershell
powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.contract.ps1
```

Failed because `qa/admin-cli-bootrun-smoke.ps1` was missing.

Runtime RED:

```powershell
powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.ps1
```

Failed because Spring could not instantiate `GlobalAdminRoleCommandRunner`: no default constructor was available and the intended injection constructor was not marked.

Focused RED:

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.GlobalAdminRoleCommandRunnerTest
```

Failed at `marksSpringBootRunnerConstructorForInjection`.

GREEN:

```powershell
.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.GlobalAdminRoleCommandRunnerTest
powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.contract.ps1
powershell -ExecutionPolicy Bypass -File qa\admin-cli-bootrun-smoke.ps1
```

Notes:

- Focused runner test passed.
- Contract script printed `ADMIN_CLI_BOOTRUN_CONTRACT_PASS`.
- Actual smoke printed `ADMIN_CLI_BOOTRUN_SMOKE_PASS`.

## Six-Metric Review Score

| Metric | Score |
| --- | ---: |
| Plan/Design Alignment | 5/5 |
| TDD Evidence | 5/5 |
| Security/Privacy | 4/5 |
| Integration Compatibility | 5/5 |
| Documentation/Traceability | 5/5 |
| Residual Risk Control | 4/5 |

Total: 28/30

Decision: PASS
