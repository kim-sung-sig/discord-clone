# T129 Ephemeral Operator Token Flow Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T129 Ephemeral Operator Token Flow

## Findings

- T112 made operator tokens easier to enter, but it still stored the manually entered token directly for dashboard calls.
- A short-lived issued token reduces the blast radius of a copied browser session token.
- Keeping the existing bootstrap secret as the exchange credential avoids introducing a separate provisioning channel in this slice.
- The route-level access configuration must attach the ephemeral store only when the bootstrap token is configured; otherwise local development open mode would accidentally become fail-closed.

## Risk Review

| Risk | Control |
| --- | --- |
| Raw token persisted in server data | Store hashes and audit token hash prefixes only. |
| Bootstrap token directly authorizes dashboard APIs | Guarded routes disable static token fallback when the token store is attached. |
| Local dev access breaks with no guard configured | Routes attach the token store only when `NUXT_SECURITY_DASHBOARD_TOKEN` exists. |
| Token remains usable after UI clear | Clear calls the revoke endpoint before removing session storage. |
| Token leaked in DOM | UI renders expiry only; tests assert token strings are not present. |

## Remaining Gaps

- The default token store is in-memory, so multi-node production should add a durable central store.
- Audit entries are not yet exposed through a guarded review API.
