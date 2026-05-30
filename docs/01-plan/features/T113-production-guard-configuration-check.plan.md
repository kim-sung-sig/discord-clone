# T113 Production Guard Configuration Check Plan

## Objective

Prevent the security dashboard from remaining open in production when no backend, JWT, admin allowlist, or operator-token guard is configured.

## Current State

- T105 preserves local development open mode when no guard is configured.
- The same open mode would also apply in production if all guard configuration is missing.
- T111 provides backend-owned admin authority through `/api/users/@me`, but production still needs configuration enforcement.

## Scope

1. Extend security dashboard access config with a production/require-guard flag.
2. Keep local development open mode when no guard is configured.
3. Fail closed in production when no guard is configured.
4. Add an explicit opt-in env variable for requiring dashboard guard outside production.
5. Document recommended production configuration.

## Acceptance Criteria

- No configured guard in local mode still returns `local-dev-open`.
- No configured guard in production returns denied.
- `NUXT_SECURITY_DASHBOARD_REQUIRE_GUARD=true` also returns denied when no guard is configured.
- Configured backend/JWT/operator token guards still work in production.
- Focused tests, full web tests, build, and whitespace checks pass.

