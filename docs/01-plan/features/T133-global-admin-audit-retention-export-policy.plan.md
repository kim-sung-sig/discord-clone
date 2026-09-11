---
slug: T133-global-admin-audit-retention-export-policy
ticket: T133
phase: plan
hub: "[[T133-global-admin-audit-retention-export-policy]]"
---
> 🧭 [[T133-global-admin-audit-retention-export-policy]] · PDCA: **plan** → [[T133-global-admin-audit-retention-export-policy.design]] → [[T133-global-admin-audit-retention-export-policy.analysis]] → [[T133-global-admin-audit-retention-export-policy.report]] → [[T133-global-admin-audit-retention-export-policy.feedback]]

# T133 Global Admin Audit Retention And Export Policy Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T133 Global Admin Audit Retention And Export Policy

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Global admin audit review existed, but retention and export rules were not visible to operators or API consumers. |
| Solution | Add a 365-day retention cutoff and include retention/export policy metadata in the guarded audit-log response. |
| Operator Effect | Security admins can review and export bounded JSON evidence with clear retention expectations. |
| Core Value | Admin audit evidence becomes safer to operate before compliance or incident-review usage. |

## Scope

- Filter global admin audit review to entries inside the active retention window.
- Add retention metadata to `GET /api/admin/global-roles/audit-log`.
- Add export policy metadata to the same guarded response.
- Update the global admin runbook with export handling rules.

## Out of Scope

- Physical database pruning or archival jobs.
- CSV/PDF export formats.
- Legal-hold workflow.

## Success Criteria

- Audit review omits entries outside the 365-day retention window.
- Response includes retention max age and export policy.
- Export policy remains `SECURITY_ADMIN` guarded and request bounded.
- Focused backend tests pass.
