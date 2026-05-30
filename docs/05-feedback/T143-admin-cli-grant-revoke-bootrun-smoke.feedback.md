# T143 Admin CLI Grant/Revoke BootRun Smoke Feedback

Date: 2026-05-21
Slice: T143 Admin CLI Grant/Revoke BootRun Smoke

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T176 Admin CLI NOOP BootRun Smoke Coverage | P3 | Duplicate grant and missing-role revoke are unit-tested, but the real bootRun smoke now covers only `APPLIED` grant/revoke paths. |

## Notes

- Keep NOOP smoke lower priority because it would add more Spring Boot launches to an already heavier QA script.
