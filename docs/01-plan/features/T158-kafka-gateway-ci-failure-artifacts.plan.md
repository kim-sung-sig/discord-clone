# T158 Kafka Gateway CI Failure Artifacts Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T158 Kafka Gateway CI Failure Artifacts

## Executive Summary

| View | Content |
| --- | --- |
| Problem | `qa-central-kafka` can fail in CI without retaining Docker, Compose, Kafka broker, or Gradle test evidence. |
| Solution | Add a Kafka-specific failure artifact script, contract, CI failure collection step, and artifact upload. |
| Operator Effect | Failed Kafka Gateway CI runs become diagnosable without rerunning the job locally. |
| Core Value | Distributed realtime smoke failures leave enough evidence to separate broker readiness, container, and application test failures. |

## Scope

- Add `qa/central-kafka-ci-artifacts.ps1`.
- Collect Docker state, Compose state/config, `ms-kafka` logs, and Gradle test reports/results.
- Add a contract for Kafka artifact requirements.
- Wire artifact collection into `qa-central-kafka` with `if: failure()`.
- Upload `qa/artifacts/central-kafka` from CI.

## Out of Scope

- Changing Kafka Gateway event-bus behavior.
- Adding retry/DLQ policy.
- Changing broker topology.
- Replacing Gradle test execution.

## Success Criteria

- Kafka artifact contract fails before implementation and passes after implementation.
- CI workflow contract requires Kafka artifact collection/upload.
- Local artifact script can run independently and writes artifacts under `qa/artifacts/central-kafka/ci`.
- Existing Kafka Gateway smoke still passes.
