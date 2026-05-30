# T154 Central Redis Smoke CI Gate Feedback

Date: 2026-05-20
Slice: T154 Central Redis Smoke CI Gate

## Improvement Tasks

| Task | Priority | Description |
| --- | --- | --- |
| T157 Central Redis CI Failure Artifacts | P2 | Upload Docker Compose state, Redis logs, and Gradle/Vitest reports when `qa-central-redis` fails in CI. |

## Notes

- Failure artifacts will matter once the smoke runs remotely because local Docker state is not visible after a failed CI job.
