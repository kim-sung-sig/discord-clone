# T129 Ephemeral Operator Token Flow Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T129 Ephemeral Operator Token Flow

## Executive Summary

| View | Content |
| --- | --- |
| Problem | The security dashboard supported a manually entered operator token, but that token behaved like a long-lived direct access credential. |
| Solution | Treat the configured operator token as a bootstrap secret and exchange it for a short-lived server-issued dashboard token. |
| Operator Effect | Operators can unlock the dashboard with a 15-minute token and clear/revoke the session from the UI. |
| Core Value | Break-glass access becomes shorter-lived, revocable, and auditable without rendering token values. |

## Scope

- Add an operator token store that records token hashes, expiry, revocation, and audit events.
- Add an exchange endpoint for issuing short-lived operator tokens from the bootstrap secret.
- Add a revoke endpoint for clearing the current short-lived token.
- Update dashboard authorization routes to accept issued tokens when bootstrap token mode is configured.
- Update `/security` to exchange bootstrap tokens and display only expiry metadata.

## Out of Scope

- Multi-node durable token storage.
- Operator token audit review UI.
- Replacing backend/JWT admin authorization.

## Success Criteria

- Issued tokens expire after 15 minutes by default.
- Raw issued tokens are never stored in audit records.
- Revoked tokens no longer authorize dashboard access.
- The UI stores the issued token in session storage but never renders it.
- Local development open mode remains intact when no dashboard guard is configured.
