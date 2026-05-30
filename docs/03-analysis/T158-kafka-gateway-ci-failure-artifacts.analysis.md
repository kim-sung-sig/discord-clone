# T158 Kafka Gateway CI Failure Artifacts Analysis

Date: 2026-05-20
Slice: T158 Kafka Gateway CI Failure Artifacts

## Findings

- `qa-central-kafka` had a real smoke gate but no failure artifact collection.
- Redis CI already had an artifact pattern that could be mirrored safely.
- Kafka failures need broker logs in addition to generic Docker and Compose state.
- Gradle already writes HTML and XML test outputs for `CentralKafkaGatewayEventBusSmokeTest`.

## Security Review

- The artifact script captures container state, Compose config, broker logs, and Gradle reports only.
- No application secrets are intentionally read or printed by the new script.
- CI upload is limited to `qa/artifacts/central-kafka`, not the full workspace.
- The step runs on failure only, reducing routine artifact exposure.

## Residual Risk

- Broker logs can include operational configuration details; CI artifact access should stay restricted to repository operators.
- A future retry/DLQ policy task still owns malformed event and consumer failure semantics.
