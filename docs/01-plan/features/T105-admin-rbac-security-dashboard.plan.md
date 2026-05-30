# T105 Admin RBAC Security Dashboard Plan

## Objective

Protect the browser security dashboard telemetry API with admin-oriented access control while preserving the existing operator-token fallback for local and break-glass operations.

## Current State

- `/api/security/csp-telemetry` currently accepts every request when `NUXT_SECURITY_DASHBOARD_TOKEN` is empty.
- When an operator token is configured, the endpoint checks only `x-operator-token`.
- Backend access tokens are HS256 JWTs with `sub`, `iat`, and `exp` claims.
- Backend `/api/users/@me` can validate the bearer token and return the authenticated user profile, but it does not currently expose global admin roles.

## Scope

1. Add a reusable Nuxt server guard for security dashboard access.
2. Support backend token verification first when a check URL is configured.
3. Support local JWT verification when a shared JWT secret and admin allowlist are configured.
4. Keep the operator token as the final fallback.
5. Keep local development open only when no dashboard guard is configured.
6. Document the remaining limitation: backend verification can only prove identity until a backend admin role contract exists.

## Acceptance Criteria

- Backend verification is attempted before JWT and operator token paths.
- Backend verification can authorize users by admin flags, roles, scopes, or configured user ids.
- Local JWT verification rejects invalid, expired, and non-admin tokens.
- Operator token remains supported but no longer hides stronger configured guards.
- The telemetry API returns `403` when all configured guard methods reject the request.
- Focused tests, full web tests, build, and whitespace checks pass.

