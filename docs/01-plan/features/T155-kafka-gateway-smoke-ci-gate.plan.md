# T155 Kafka Gateway Smoke CI Gate Plan

Date: 2026-05-20
Slice: T155 Kafka Gateway Smoke CI Gate

## Objective

Promote the central Kafka Gateway smoke from a local-only script to a repeatable CI gate.

## Current State

- T151 added `qa/central-kafka-gateway-smoke.ps1`.
- The smoke proves node-a publish and node-b listener delivery through a real broker.
- The script used `gradlew.bat`, so it needed Linux-compatible Gradle invocation before running in GitHub Actions.

## Scope

1. Extend the CI workflow contract to require a Kafka Gateway smoke job.
2. Add a GitHub Actions job that runs Kafka smoke contracts and the smoke script.
3. Make `qa/central-kafka-gateway-smoke.ps1` select the correct Gradle wrapper per platform.
4. Ensure native command failures fail the PowerShell script.

## Acceptance Criteria

- CI workflow contract fails before the job exists and passes after implementation.
- `qa-central-kafka` job runs central Kafka Gateway smoke contracts.
- `qa-central-kafka` job runs `qa/central-kafka-gateway-smoke.ps1`.
- Local Kafka smoke still passes after script portability changes.
- T155-touched files pass `git diff --check`.
