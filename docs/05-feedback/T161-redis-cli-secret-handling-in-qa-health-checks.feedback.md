# T161 Redis CLI Secret Handling In QA Health Checks Feedback

Date: 2026-05-20
Slice: T161 Redis CLI Secret Handling In QA Health Checks

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T131 Redis CSP limiter lifecycle metrics | P2 | Redis QA secret handling is improved, but runtime limiter lifecycle visibility remains. |

## Notes

- T161 closes the direct `redis-cli -a` password exposure pattern in QA health checks.
