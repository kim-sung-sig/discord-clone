---
slug: T151-kafka-gateway-docker-smoke-test
ticket: T151
phase: feedback
hub: "[[T151-kafka-gateway-docker-smoke-test]]"
---
> 🧭 [[T151-kafka-gateway-docker-smoke-test]] · PDCA: [[T151-kafka-gateway-docker-smoke-test.plan]] → [[T151-kafka-gateway-docker-smoke-test.design]] → [[T151-kafka-gateway-docker-smoke-test.analysis]] → [[T151-kafka-gateway-docker-smoke-test.report]] → **feedback**

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
