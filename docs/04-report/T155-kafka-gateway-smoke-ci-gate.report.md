---
slug: T155-kafka-gateway-smoke-ci-gate
ticket: T155
phase: report
hub: "[[T155-kafka-gateway-smoke-ci-gate]]"
---
> 🧭 [[T155-kafka-gateway-smoke-ci-gate]] · PDCA: [[T155-kafka-gateway-smoke-ci-gate.plan]] → [[T155-kafka-gateway-smoke-ci-gate.design]] → [[T155-kafka-gateway-smoke-ci-gate.analysis]] → **report** → [[T155-kafka-gateway-smoke-ci-gate.feedback]]

# T155 Kafka Gateway Smoke CI Gate Report

Date: 2026-05-20
Slice: T155 Kafka Gateway Smoke CI Gate

## Completed

- Added CI workflow contract requirements for the Kafka Gateway smoke job.
- Added `.github/workflows/ci.yml` job `qa-central-kafka`.
- Made `qa/central-kafka-gateway-smoke.ps1` portable across Windows and Linux Gradle wrappers.
- Added native command exit-code enforcement inside the Kafka smoke script.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` failed because `qa-central-kafka` was missing.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-gateway-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-gateway-smoke.ps1` passed with Gradle tasks executed and `CENTRAL_KAFKA_GATEWAY_SMOKE_PASS`.

## Notes

- The CI job runs the Kafka smoke script instead of duplicating its broker startup logic in YAML.
