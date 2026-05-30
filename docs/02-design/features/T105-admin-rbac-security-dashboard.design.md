# T105 Admin RBAC Security Dashboard Design

## Guard Order

The security dashboard API evaluates access in this order:

1. Backend verification
2. Local JWT verification
3. Operator token fallback
4. Local development open mode only when no guard is configured

This makes backend authority the preferred source while retaining deterministic local verification and an operational fallback.

## Backend Verification

Configuration:

- `NUXT_SECURITY_DASHBOARD_AUTH_CHECK_URL`
- `NUXT_SECURITY_DASHBOARD_ADMIN_USER_IDS`
- `NUXT_SECURITY_DASHBOARD_ADMIN_ROLES`
- `NUXT_SECURITY_DASHBOARD_ADMIN_SCOPES`

Runtime behavior:

- Forward the incoming `Authorization` header to the configured URL.
- Treat non-2xx responses as unauthorized.
- Accept flexible backend response shapes:
  - `admin: true` or `isAdmin: true`
  - `roles` intersecting configured admin roles
  - `scopes`/`scope` intersecting configured admin scopes
  - `id`, `userId`, `sub`, or nested `user.id` matching configured admin user ids

## Local JWT Verification

Configuration:

- `NUXT_SECURITY_DASHBOARD_JWT_SECRET`
- `NUXT_SECURITY_DASHBOARD_ADMIN_USER_IDS`
- `NUXT_SECURITY_DASHBOARD_ADMIN_ROLES`
- `NUXT_SECURITY_DASHBOARD_ADMIN_SCOPES`

Runtime behavior:

- Accept only HS256 bearer tokens.
- Verify signature using Node `crypto`.
- Reject expired tokens.
- Authorize by user id, role, or scope allowlists.
- Because current backend-issued JWTs only include `sub`, local JWT authorization currently depends on `NUXT_SECURITY_DASHBOARD_ADMIN_USER_IDS`.

## Operator Token Fallback

Configuration:

- `NUXT_SECURITY_DASHBOARD_TOKEN`

Runtime behavior:

- Accept `x-operator-token` only when it exactly matches the configured value.
- This remains a fallback after backend/JWT checks.

## Local Development Open Mode

When no backend check URL, JWT secret, admin allowlist, or operator token is configured, the guard allows access and marks the method as `local-dev-open`. This preserves the existing local developer behavior while making production configuration explicit.

