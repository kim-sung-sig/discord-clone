# T34 Session & Account Security Hardening Design

## Backend Design
- `RefreshSession` is a domain record in the identity module. It owns expiry, idempotent revoke, and rotation invariants.
- `AuthStore` owns refresh-session persistence so boot adapters can implement in-memory and PostgreSQL variants without leaking JDBC into domain modules.
- Refresh tokens are stored only as SHA-256 hashes. Raw refresh tokens are issued to the browser as `dc_refresh` httpOnly cookies.
- `POST /api/auth/refresh` rotates the refresh token. Reuse of a revoked token triggers refresh-session family revocation for the user.
- `GET /api/auth/sessions` lists the authenticated user's sessions. `DELETE /api/auth/sessions/{sessionId}` revokes only sessions belonging to that user.
- `POST /api/auth/logout` revokes both bearer access token and refresh cookie session when present.
- New-device login emits a structured warning log as an audit/notification candidate.

## Frontend Design
- The Nuxt auth store keeps access tokens only in Pinia memory.
- Session restoration calls `POST /api/auth/refresh` with `credentials: include`; no browser storage snapshot is written.
- REST client always sends `credentials: include` so same code path supports refresh cookie, logout, and future same-site cookie security.
- User panel exposes session list, revoke, and logout controls while preserving the existing shell layout.

## Security Rationale
- Memory-only access tokens reduce XSS blast radius compared with localStorage/sessionStorage persistence.
- httpOnly refresh cookies prevent JavaScript token extraction.
- Rotation plus reuse detection converts stolen refresh-token replay into a detectable and contained incident.
- Device/session list provides user-facing account recovery controls without requiring admin intervention.

## Regression Controls
- API tests cover cookie issuance, rotation, reuse detection, session listing, session revoke, logout, and suspicious-login log emission.
- PostgreSQL tests cover save, lookup, rotate, list, and revoke persistence behavior.
- Frontend tests cover auth paths, `credentials: include`, and absence of access tokens in browser storage.
- CORS tests cover credentialed local Nuxt development origins.
