# T158 Kafka Gateway CI Failure Artifacts Report

Date: 2026-05-20
Slice: T158 Kafka Gateway CI Failure Artifacts

## Completed

- Added `qa/central-kafka-ci-artifacts.contract.ps1`.
- Added `qa/central-kafka-ci-artifacts.ps1`.
- Updated `.github/workflows/ci.yml` to collect Kafka artifacts on `qa-central-kafka` failure.
- Updated `.github/workflows/ci.yml` to upload `central-kafka-artifacts`.
- Updated `qa/ci-workflow.contract.ps1` with Kafka artifact requirements.

## Verification

- RED observed first:
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-ci-artifacts.contract.ps1` failed because the artifact script was missing.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` failed because CI did not run Kafka artifact collection.
- GREEN after implementation:
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-ci-artifacts.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/ci-workflow.contract.ps1` passed.
  - `$env:CENTRAL_KAFKA_ARTIFACT_DIR='qa/artifacts/central-kafka/ci'; powershell -ExecutionPolicy Bypass -File qa/central-kafka-ci-artifacts.ps1` passed and emitted `CENTRAL_KAFKA_CI_ARTIFACTS_COLLECTED`.
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-gateway-smoke.contract.ps1` passed.
  - `powershell -ExecutionPolicy Bypass -File qa/central-kafka-gateway-smoke.ps1` passed and emitted `CENTRAL_KAFKA_GATEWAY_SMOKE_PASS`.

## Artifact Evidence

- `qa/artifacts/central-kafka/ci/docker-ps.txt`
- `qa/artifacts/central-kafka/ci/docker-compose-ps.txt`
- `qa/artifacts/central-kafka/ci/docker-compose-config.txt`
- `qa/artifacts/central-kafka/ci/docker-compose-ms-kafka.log`
- `qa/artifacts/central-kafka/ci/docker-ms-kafka.log`
- `qa/artifacts/central-kafka/ci/gradle-test-report/`
- `qa/artifacts/central-kafka/ci/gradle-test-results/TEST-com.example.discord.gateway.CentralKafkaGatewayEventBusSmokeTest.xml`
