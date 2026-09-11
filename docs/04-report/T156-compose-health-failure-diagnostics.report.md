---
slug: T156-compose-health-failure-diagnostics
ticket: T156
phase: report
hub: "[[T156-compose-health-failure-diagnostics]]"
---
> 🧭 [[T156-compose-health-failure-diagnostics]] · PDCA: [[T156-compose-health-failure-diagnostics.plan]] → [[T156-compose-health-failure-diagnostics.design]] → [[T156-compose-health-failure-diagnostics.analysis]] → **report** → [[T156-compose-health-failure-diagnostics.feedback]]

# T156 Compose Health Failure Diagnostics Report

Date: 2026-05-20
Slice: T156 Compose Health Failure Diagnostics

## Completed

- Extended the central compose health contract with diagnostics requirements.
- Added `Write-HealthDiagnostics` to `qa/central-compose-health.ps1`.
- Added Docker, Compose, Windows port, and Linux port diagnostics.
- Wired diagnostics into Postgres, Redis, and Kafka startup/readiness failures.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health.contract.ps1` failed because diagnostics were missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health.ps1` passed and emitted `CENTRAL_COMPOSE_HEALTH_PASS`.

## Notes

- A controlled failing diagnostic smoke is tracked separately so normal resource containers do not need to be disrupted.
