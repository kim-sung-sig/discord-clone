# T132 Global Admin Audit Review API Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T132 Global Admin Audit Review API

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Global admin role audit entries existed in storage but required direct store/database access to review. |
| Solution | Add a guarded backend API for `SECURITY_ADMIN` users to list global role audit entries. |
| Operator Effect | Security admins can review privileged role changes through a controlled endpoint. |
| Core Value | High-impact admin role mutations become inspectable without broad database access. |

## Scope

- Add `GET /api/admin/global-roles/audit-log`.
- Require a valid bearer token with backend-owned `SECURITY_ADMIN`.
- Support optional `targetUserId` filter and bounded `limit`.
- Extend in-memory and JDBC audit store queries.
- Add OpenAPI contract coverage.

## Out of Scope

- Audit export/archive.
- Retention policy.
- Duplicate-safe grant result semantics.

## Success Criteria

- Security admin requests return audit entries.
- Non-admin requests return 403.
- Invalid/missing bearer tokens remain unauthorized.
- OpenAPI contract stays fresh.
- Backend regression tests pass.
