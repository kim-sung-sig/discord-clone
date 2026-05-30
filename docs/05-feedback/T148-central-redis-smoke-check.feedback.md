# T148 Central Redis Smoke Check Feedback

Date: 2026-05-20
Slice: T148 Central Redis Smoke Check

## Improvement Tasks

| Task | Priority | Description |
| --- | --- | --- |
| T154 Central Redis Smoke CI Gate | P1 | Run `qa/central-redis-smoke.ps1` from a CI or repeatable QA profile when Docker is available, including central Redis lifecycle setup and cleanup policy. |

## Notes

- The smoke is currently local and opt-in.
- CI integration should decide whether to reuse a shared central resource container or start an isolated Compose project per job.
