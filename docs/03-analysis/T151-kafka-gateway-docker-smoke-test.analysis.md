# T151 Kafka Gateway Docker Smoke Test Analysis

Date: 2026-05-20
Slice: T151 Kafka Gateway Docker Smoke Test

## Findings

- Existing T146 tests prove serialization, sanitization, and same-node suppression without a real broker.
- A real broker smoke is needed because broker availability, topic creation, producer serialization, and consumer deserialization can fail even when the unit tests pass.
- The local central Kafka resource may already run as a standalone `ms-kafka` container, so the smoke must reuse it instead of always starting Compose.

## Verification Strategy

- Keep the smoke env-gated to avoid requiring Docker for normal unit test runs.
- Use a unique topic prefix per run to avoid cross-run messages.
- Use a real Kafka producer and consumer while keeping the test focused on Gateway event bus behavior.

## Follow-Up

- Promote this smoke to a CI/QA gate once central Docker resource lifecycle is standardized.
