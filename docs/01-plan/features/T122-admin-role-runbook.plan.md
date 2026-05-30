# T122 Admin Role Runbook Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T122 Admin Role Runbook

## Executive Summary

| View | Content |
| --- | --- |
| Problem | T118/T119/T132 delivered admin role tooling and audit visibility, but operators lacked a concise production procedure. |
| Solution | Create a runbook for resolving a user ID, granting `SECURITY_ADMIN`, verifying access, reviewing audit evidence, and revoking access. |
| Operator Effect | Admin role changes can be performed consistently with pre-checks, rollback, and evidence capture. |
| Core Value | Privileged access changes become operationally repeatable and less error-prone. |

## Scope

- Create `docs/runbooks/global-admin-role-runbook.md`.
- Include grant, list, verify, audit review, and revoke commands.
- Include failure handling and rollback guidance.
- Add a contract script that checks for required operational/security content.

## Out of Scope

- New admin role APIs.
- Audit retention/export policy.
- Duplicate-safe grant result behavior.

## Success Criteria

- Runbook includes actual `admin-cli,postgres` command properties.
- Runbook avoids passing database passwords through Gradle `--args`.
- Runbook includes `/api/users/@me` and `/api/admin/global-roles/audit-log` verification.
- Contract script passes.
