# T169 Durable Operator Token Store Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T169 Durable Operator Token Store

## Executive Summary

| View | Content |
| --- | --- |
| Problem | T129 issued short-lived operator tokens, but the default store was in-memory and therefore unsafe for multi-node production. |
| Solution | Add a Postgres-backed operator token store and select it through environment configuration. |
| Operator Effect | Issued dashboard tokens, revocations, and audit rows survive process restarts and are shared across nodes. |
| Core Value | Break-glass dashboard access becomes production-usable in centralized deployments. |

## Scope

- Add a Postgres implementation of `SecurityDashboardOperatorTokenStore`.
- Add token and audit tables to the existing CSP/security dashboard migration.
- Add default store selection through a dedicated Postgres URL or the shared CSP telemetry Postgres URL.
- Add gated Postgres integration coverage.

## Out of Scope

- Operator token audit review UI.
- Token pruning/retention job.
- Cross-service key management.

## Success Criteria

- Postgres stores only token hashes, never raw tokens.
- Revoked or expired tokens fail verification across store instances.
- Audit rows contain token hash prefixes only.
- Default store selects Postgres when a database URL is configured.
