---
slug: T153-compose-health-gate-script
ticket: T153
phase: report
hub: "[[T153-compose-health-gate-script]]"
---
> 🧭 [[T153-compose-health-gate-script]] · PDCA: [[T153-compose-health-gate-script.plan]] → [[T153-compose-health-gate-script.design]] → [[T153-compose-health-gate-script.analysis]] → **report** → [[T153-compose-health-gate-script.feedback]]

# T153 Compose Health Gate Script Report

Date: 2026-05-20
Slice: T153 Compose Health Gate Script

## Completed

- Added a central compose health contract.
- Added `qa/central-compose-health.ps1`.
- Implemented readiness checks for central Postgres, Redis, and Kafka.
- Supported both existing standalone central containers and Compose-started services.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health.contract.ps1` failed because the health script was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-compose-health.ps1` passed and emitted `CENTRAL_COMPOSE_HEALTH_PASS`.

## Notes

- The script did not need to start new containers in the verified run because central resources were already running.
