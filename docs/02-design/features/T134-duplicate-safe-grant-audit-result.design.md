# T134 Duplicate-Safe Grant Audit Result Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T134 Duplicate-Safe Grant Audit Result

## Design

`AuthStore.grantGlobalRole(UUID, String)` returns `true` when the role was newly inserted and `false` when it already existed.

| Store | Implementation |
| --- | --- |
| In-memory | Return the result of `Set.add(canonicalRole)`. |
| JDBC/Postgres | Return whether `INSERT ... ON CONFLICT DO NOTHING` affected one row. |
| CLI runner | Map `true` to `APPLIED` and `false` to `NOOP`. |

## Operator Contract

- A successful first grant prints `granted SECURITY_ADMIN to <user>`.
- A duplicate grant prints `SECURITY_ADMIN was already present for <user>`.
- Both paths write audit entries.
- Duplicate grant audit entries use `action: GRANT` and `result: NOOP`.

## Security Review

- The confirmation gate remains required for all mutating grant commands.
- Unknown users are still rejected before mutation.
- The change improves audit fidelity without adding new data exposure.
