# T153 Compose Health Gate Script Feedback

Date: 2026-05-20
Slice: T153 Compose Health Gate Script

## Improvement Tasks

| Task | Priority | Description |
| --- | --- | --- |
| T156 Compose Health Failure Diagnostics | P2 | When a central resource fails readiness, print port owner, matching Docker containers, and the last relevant container status to speed up local recovery. |

## Notes

- T153 now provides the common local readiness gate.
- CI promotion is tracked separately by the Redis and Kafka smoke gate tasks.
