# T141 Admin CLI BootRun Smoke CI Gate Feedback

Date: 2026-05-20
Slice: T141 Admin CLI BootRun Smoke CI Gate

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T142 Admin CLI Smoke Database Isolation | P2 | Still relevant: the default smoke uses the shared target database unless the caller passes a temporary database. |
| T143 Admin CLI Grant/Revoke BootRun Smoke | P2 | Still relevant: this CI gate covers the non-mutating list command only. |

## Notes

- T141 closes the CI gate for the existing admin CLI list smoke.
- Security cleanup was applied directly by moving DB credentials out of `bootRun --args`.
