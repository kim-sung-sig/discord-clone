# T113 Production Guard Configuration Check Analysis

## Result

The security dashboard now fails closed when a configured guard is required but missing.

## Behavior

- Local development still allows open dashboard access when no guard is configured.
- `NODE_ENV=production` requires at least one dashboard guard.
- `NUXT_SECURITY_DASHBOARD_REQUIRE_GUARD=true` also requires at least one dashboard guard outside production.
- Missing guard in required mode returns:
  - `allowed: false`
  - `method: denied`
  - `reason: security dashboard guard is not configured`

## Recommended Production Configuration

Use T111 backend authority:

`NUXT_SECURITY_DASHBOARD_AUTH_CHECK_URL=https://api.example.com/api/users/@me`

The backend returns `admin: true` for `SECURITY_ADMIN` users.

