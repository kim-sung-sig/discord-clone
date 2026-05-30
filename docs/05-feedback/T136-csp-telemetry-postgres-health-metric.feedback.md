# T136 CSP Telemetry Postgres Health Metric Feedback

Date: 2026-05-20
Slice: T136 CSP Telemetry Postgres Health Metric

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T131 Redis CSP limiter lifecycle metrics | P2 | CSP storage health is visible, but Redis limiter health/churn is still separate. |
| T137 CSP Telemetry SQLite Legacy Cleanup Note | P2 | Operators can see Postgres health, but legacy SQLite cleanup guidance remains. |

## Notes

- T136 closes the Postgres telemetry health visibility gap inside `/security`.
