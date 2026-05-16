# T34 Session & Account Security Hardening Feedback

## Decisions
- Keep refresh session behavior in the identity domain model and persistence behind `AuthStore`.
- Use `dc_refresh` as an httpOnly cookie scoped to `/api/auth`.
- Use memory-only access token storage in the Nuxt store.
- Keep suspicious login as a structured log candidate for now; durable audit-event storage can be promoted in a later audit expansion task.

## Issues Found
- Frontend session restore previously used `sessionStorage`, which violates the target browser token policy.
- Credentialed refresh cookies require `Access-Control-Allow-Credentials: true` for local Nuxt API calls.
- PostgreSQL `CHAR(64)` hash columns pad shorter test values; JDBC mapping trims values while production hashes remain fixed 64-character SHA-256 hex.
- AuthController tests used a fixed `X-Forwarded-For` and could be rate-limited by earlier tests; helper now emits unique test IPs.
- Running Nuxt build and Vitest concurrently can race generated `#app-manifest` files. Keep those verification commands sequential.

## Follow-Up Candidates
- Promote suspicious login log candidates into a durable `audit_security_events` table with notification fan-out.
- Add session metadata for IP hash, last-used-at, and current-session marker.
- Add SameSite/Secure profile-aware cookie configuration before production TLS deployment.
