# T129 Ephemeral Operator Token Flow Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T129 Ephemeral Operator Token Flow

## Design

Add `security-dashboard-operator-token-store.ts` with an operator token store interface:

| Method | Purpose |
| --- | --- |
| `issue(command)` | Generate a random `sdo_...` token and store only its hash plus expiry metadata. |
| `verify(token, now)` | Authorize a valid, unexpired, unreleased token. |
| `revoke(command)` | Mark the current token revoked and append an audit event. |
| `audit(limit)` | Return hash-prefix audit events without raw token values. |

## API Flow

1. `POST /api/security/operator-token/exchange` checks the configured bootstrap token.
2. If valid, it issues a 15-minute token and returns the raw token only once.
3. Guarded dashboard APIs attach the token store only when a bootstrap token is configured.
4. Static bootstrap tokens do not directly authorize those guarded APIs in token-store mode.
5. `POST /api/security/operator-token/revoke` revokes the current issued token.

## UI Flow

1. Operator enters the bootstrap token in `/security`.
2. The page exchanges it for a short-lived token.
3. The short-lived token and expiry are stored in `sessionStorage`.
4. The page renders only the expiry.
5. Clear revokes the short-lived token and removes local session storage.

## Security Review

- Raw tokens are not stored in server audit records.
- Raw tokens are not rendered in the page.
- Bootstrap token is sent only to the exchange endpoint.
- Guarded dashboard endpoints use the issued token after exchange.
- Vue template interpolation is used for expiry metadata.
