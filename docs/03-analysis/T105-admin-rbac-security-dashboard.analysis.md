# T105 Admin RBAC Security Dashboard Analysis

## Result

The browser security dashboard telemetry API now has a layered admin access guard:

1. Backend verification when `NUXT_SECURITY_DASHBOARD_AUTH_CHECK_URL` is configured.
2. Local HS256 JWT verification when `NUXT_SECURITY_DASHBOARD_JWT_SECRET` is configured.
3. Operator token fallback through `NUXT_SECURITY_DASHBOARD_TOKEN`.
4. Local development open mode only when no guard is configured.

## Findings

- Backend-issued JWTs currently contain only `sub`, `iat`, and `exp`.
- Because roles are not embedded in the JWT, local JWT authorization depends on `NUXT_SECURITY_DASHBOARD_ADMIN_USER_IDS`.
- Backend verification can authorize flexible response shapes, including admin flags, roles, scopes, and configured user ids.
- The `/security` page now forwards the in-memory auth access token as `Authorization: Bearer ...`.
- The page can also forward an operator token from `sessionStorage` key `dc_security_dashboard_operator_token`.

## Residual Risk

- A first-class backend admin role contract does not yet exist.
- Operator token entry is currently operational-only through `sessionStorage`; there is no dedicated UI for it.
- Local development remains open when no guard configuration exists, matching previous behavior but requiring production environment discipline.

