# T126 CSP Alert Persistence Feedback

Date: 2026-05-20
Slice: T126 CSP Alert Persistence

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T135 Explicit CSP Telemetry Postgres Migration | P2 | Add reviewed migration ownership for `csp_alert_transitions` along with existing CSP telemetry tables. |
| T127 CSP alert acknowledgement workflow | P2 | Operators can review alert history but still cannot acknowledge or suppress an already-triaged incident. |

## Notes

- T126 closes the persistence gap for CSP alert active/clear transitions.
