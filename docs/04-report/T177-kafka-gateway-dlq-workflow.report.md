# T177 Kafka Gateway DLQ Workflow Report

Date: 2026-05-21
Slice: T177 Kafka Gateway DLQ retention, alert, and replay workflow

## Completed

- Added `docs/runbooks/kafka-gateway-dlq-runbook.md`.
- Added `qa/kafka-gateway-dlq-runbook.contract.ps1`.
- Added Kafka profile DLQ policy defaults for 168-hour retention and alert threshold 1.
- Added `.env.example` DLQ policy defaults.
- Wired the DLQ runbook contract into the CI workflow contract and GitHub Actions Kafka smoke contract step.

## Verification

- RED observed first:
  - `pwsh qa/kafka-gateway-dlq-runbook.contract.ps1` failed because the runbook was missing.
  - `pwsh qa/ci-workflow.contract.ps1` failed because CI did not run the new DLQ contract.
- GREEN after implementation:
  - `pwsh qa/kafka-gateway-dlq-runbook.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa\kafka-gateway-dlq-runbook.contract.ps1` passed.
  - `pwsh qa/ci-workflow.contract.ps1` passed.
  - `pwsh qa/central-runtime-resources.contract.ps1` passed.
  - `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.KafkaGatewayEventBusTest` passed.

## Notes

- This slice documents the operational workflow and policy defaults. Automated DLQ metrics and alert integration remain separate work.
