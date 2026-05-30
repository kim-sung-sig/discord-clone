# T169 Durable Operator Token Store Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T169 Durable Operator Token Store

## Findings

- T129's in-memory store is acceptable for local development but does not survive restart or coordinate across Nuxt nodes.
- The existing central Postgres URL already backs CSP telemetry, alert history, and alert acknowledgement state.
- Reusing that database by default keeps the operator token feature centralized without adding another required service.

## Risk Review

| Risk | Control |
| --- | --- |
| Raw token persistence | Store only SHA-256 token hashes and audit hash prefixes. |
| Multi-node revoke inconsistency | Postgres `revoked_at` is checked during verification. |
| Expired token reuse | Verification rejects rows with `expires_at <= now`. |
| Audit token correlation too strong | Audit exposes only 12-character hash prefix. |
| Local dev accidentally requires Postgres | Default still falls back to in-memory when no database URL exists. |

## Remaining Gaps

- Audit entries are not yet visible in `/security`; tracked as T170.
- Token retention/pruning is not implemented yet.
