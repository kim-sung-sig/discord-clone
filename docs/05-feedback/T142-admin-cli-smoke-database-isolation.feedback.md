# T142 Admin CLI Smoke Database Isolation Feedback

Date: 2026-05-21
Slice: T142 Admin CLI Smoke Database Isolation

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T175 Admin CLI custom smoke fixture non-mutating mode | P3 | The default path is isolated now, but explicit `-SmokeUserId` can still seed/update caller-provided fixtures. A future mode should verify existing users without mutation or require a stronger confirmation flag. |

## Notes

- Cleanup failure is intentionally fatal for generated fixtures so shared central database contamination is not hidden.
