# T111 Backend Global Admin Role Contract Analysis

## Result

Backend auth now owns the global admin role contract used by the security dashboard.

## Behavior

- New Postgres table: `user_global_roles`.
- New global role contract: `SECURITY_ADMIN`.
- `AuthStore` supports granting and reading global roles.
- `/api/users/@me` now returns:
  - `id`
  - `username`
  - `displayName`
  - `roles`
  - `admin`
- `admin` is true when the user has `SECURITY_ADMIN`.

## Integration With T105

Nuxt T105 backend verification can now point `NUXT_SECURITY_DASHBOARD_AUTH_CHECK_URL` at the backend `/api/users/@me` endpoint. The backend response includes `admin: true`, which the Nuxt security dashboard guard already accepts.

## Limitations

- There is no operator-facing API or CLI for granting `SECURITY_ADMIN` yet.
- Initial grants must be done through the store in tests or through a database operation in local/prod environments.

