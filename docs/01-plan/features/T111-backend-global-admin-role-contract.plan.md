# T111 Backend Global Admin Role Contract Plan

## Objective

Make backend-owned global admin authority the source of truth for security dashboard access, replacing long-lived Nuxt user-id allowlists as the primary admin contract.

## Current State

- Backend `/api/users/@me` validates bearer tokens and returns user profile fields.
- Nuxt T105 backend verifier can authorize flexible responses with `admin`, `roles`, `scopes`, or allowlisted user ids.
- There is no backend-level global admin role table or response contract.

## Scope

1. Add a Postgres table for user global roles.
2. Add in-memory and JDBC store support for user global roles.
3. Expose `roles` and `admin` fields from `/api/users/@me` and auth responses.
4. Define `SECURITY_ADMIN` as the global role that maps to `admin: true`.
5. Keep existing auth JSON fields backward compatible.

## Acceptance Criteria

- A user with `SECURITY_ADMIN` in the backend store receives `admin: true`.
- Non-admin users receive `admin: false` and an empty role list.
- Postgres persists global roles and prevents duplicate grants.
- Existing signup/login/session behavior remains compatible.
- Focused backend tests and relevant web tests pass.

