# T133 Global Admin Audit Retention And Export Policy Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T133 Global Admin Audit Retention And Export Policy

## Findings

- T132 added a guarded audit review API, but its response did not tell operators how long audit evidence is retained or what export format is supported.
- Existing API limits are already bounded to 1..100, which is a good export safety boundary.
- A service-level cutoff is enough for this slice because database pruning and archival require a separate operational job.

## Risk Review

| Risk | Control |
| --- | --- |
| Unbounded audit export | Keep max entries per request at 100 and expose that as policy. |
| Non-admin audit export | Reuse the existing `SECURITY_ADMIN` guard. |
| Hidden retention assumptions | Return `maxAgeDays` and `retainsSince` in every audit-log response. |
| Loss of forensic evidence | Do not physically prune in this slice; use backup/PITR for older investigations. |

## Remaining Gaps

- Physical pruning and archive automation remain future operational work.
- Legal hold is not modeled.
