---
slug: T113-production-guard-configuration-check
ticket: T113
phase: design
hub: "[[T113-production-guard-configuration-check]]"
---
> 🧭 [[T113-production-guard-configuration-check]] · PDCA: [[T113-production-guard-configuration-check.plan]] → **design** → [[T113-production-guard-configuration-check.analysis]] → [[T113-production-guard-configuration-check.report]] → [[T113-production-guard-configuration-check.feedback]]

# T113 Production Guard Configuration Check Design

## Configuration

Add guard enforcement flag to `SecurityDashboardAccessConfig`:

- `requireConfiguredGuard`

It is true when:

- `NODE_ENV=production`, or
- `NUXT_SECURITY_DASHBOARD_REQUIRE_GUARD=true`

## Behavior

If no guard is configured:

- Local/dev mode: allow with method `local-dev-open`.
- Production/required mode: deny with reason `security dashboard guard is not configured`.

If any guard is configured, normal T105 guard order still applies:

1. Backend verification
2. JWT verification
3. Operator token fallback
4. Deny

## Recommended Production Setup

Use T111 backend authority:

`NUXT_SECURITY_DASHBOARD_AUTH_CHECK_URL=https://api.example.com/api/users/@me`

The backend response includes `admin: true` for `SECURITY_ADMIN` users.

