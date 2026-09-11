---
slug: T143-admin-cli-grant-revoke-bootrun-smoke
ticket: T143
phase: feedback
hub: "[[T143-admin-cli-grant-revoke-bootrun-smoke]]"
---
> 🧭 [[T143-admin-cli-grant-revoke-bootrun-smoke]] · PDCA: [[T143-admin-cli-grant-revoke-bootrun-smoke.plan]] → [[T143-admin-cli-grant-revoke-bootrun-smoke.design]] → [[T143-admin-cli-grant-revoke-bootrun-smoke.analysis]] → [[T143-admin-cli-grant-revoke-bootrun-smoke.report]] → **feedback**

# T143 Admin CLI Grant/Revoke BootRun Smoke Feedback

Date: 2026-05-21
Slice: T143 Admin CLI Grant/Revoke BootRun Smoke

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T176 Admin CLI NOOP BootRun Smoke Coverage | P3 | Duplicate grant and missing-role revoke are unit-tested, but the real bootRun smoke now covers only `APPLIED` grant/revoke paths. |

## Notes

- Keep NOOP smoke lower priority because it would add more Spring Boot launches to an already heavier QA script.
