# T134 Duplicate-Safe Grant Audit Result Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T134 Duplicate-Safe Grant Audit Result

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Duplicate global admin grant commands were audited as `APPLIED`, making them indistinguishable from newly applied grants. |
| Solution | Make grant operations return whether they inserted a new role and record duplicate grants as `NOOP`. |
| Operator Effect | Audit reviewers can distinguish newly granted access from idempotent duplicate commands. |
| Core Value | Security admin audit evidence becomes more accurate without changing authorization semantics. |

## Scope

- Change `AuthStore.grantGlobalRole` to return whether a role was newly inserted.
- Update in-memory and JDBC stores to return duplicate-safe grant results.
- Update the admin CLI runner to audit duplicate grants as `NOOP`.
- Update tests and runbook guidance.

## Out of Scope

- Changing revoke behavior, which already records `NOOP` for absent roles.
- Adding new audit actions.
- Changing global role authorization checks.

## Success Criteria

- First grant records `APPLIED`.
- Duplicate grant records `NOOP`.
- Postgres store returns `false` for duplicate grants.
- Focused backend tests, checkstyle, and whitespace checks pass.
