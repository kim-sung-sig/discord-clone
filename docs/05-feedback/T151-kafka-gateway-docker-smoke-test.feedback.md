# T151 Kafka Gateway Docker Smoke Test Feedback

Date: 2026-05-20
Slice: T151 Kafka Gateway Docker Smoke Test

## Improvement Tasks

| Task | Priority | Description |
| --- | --- | --- |
| T155 Kafka Gateway Smoke CI Gate | P1 | Run `qa/central-kafka-gateway-smoke.ps1` from a CI or repeatable QA profile when Docker is available, with a clear central broker lifecycle policy. |

## Notes

- The smoke uses unique topic prefixes to avoid cross-run message contamination.
- CI integration should decide whether to reuse a shared central broker or start a per-job Compose project.
