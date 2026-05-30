# T155 Kafka Gateway Smoke CI Gate Feedback

Date: 2026-05-20
Slice: T155 Kafka Gateway Smoke CI Gate

## Improvement Tasks

| Task | Priority | Description |
| --- | --- | --- |
| T158 Kafka Gateway CI Failure Artifacts | P2 | Upload Docker Compose state, broker logs, and Gradle reports when `qa-central-kafka` fails in CI. |

## Notes

- Kafka startup and broker errors are difficult to diagnose after CI teardown without explicit artifacts.
