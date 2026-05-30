# T119 Global Admin Audit Log Analysis

Date: 2026-05-19
Slice: T119 Global Admin Audit Log

## Analysis

Global admin role changes are high-impact security operations. Without an audit trail, a production operator can grant or revoke `SECURITY_ADMIN` without later reviewability. T119 closes that traceability gap at the command boundary where the mutation happens.

The store-level audit contract keeps implementation simple and testable. It avoids adding a public API before access-control requirements are clear, while still ensuring that both local and Postgres profiles can retain audit entries.

## Trade-Offs

- The audit log is queryable through `AuthStore` only, not yet exposed to administrators.
- Grant commands are recorded as `APPLIED` even when the role already existed because the current grant operation is duplicate-safe but does not return whether a row changed.
- Audit retention is intentionally not enforced in this slice to avoid deleting security evidence before a policy exists.

## Security Notes

- Actor is bounded to 128 characters.
- Role is canonicalized through `GlobalRole`.
- Unknown users fail before audit creation.
- Mutating commands still require explicit confirmation.

## Residual Risk

Audit review, export, and retention policy are still missing. These should be added before relying on the audit log as the only operational evidence source.
