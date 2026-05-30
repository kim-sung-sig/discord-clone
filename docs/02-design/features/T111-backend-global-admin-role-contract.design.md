# T111 Backend Global Admin Role Contract Design

## Data Model

Add table `user_global_roles`:

- `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`
- `role VARCHAR(64) NOT NULL`
- `granted_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- primary key `(user_id, role)`

The contract uses role string `SECURITY_ADMIN`.

## Store Contract

Extend `AuthStore` with:

- `grantGlobalRole(UUID userId, String role)`
- `globalRolesForUser(UUID userId)`

The store canonicalizes roles to uppercase trimmed strings and ignores duplicate grants.

## API Contract

`AuthController.UserResponse` becomes:

- `id`
- `username`
- `displayName`
- `roles`
- `admin`

Existing clients that read only the first three fields remain compatible. Nuxt T105 backend verifier can authorize `admin: true` from `/api/users/@me`.

## Admin Mapping

`admin` is true when `roles` contains `SECURITY_ADMIN`.

## Testing

- Controller test grants `SECURITY_ADMIN` and verifies `/api/users/@me` exposes `admin: true`.
- Controller test verifies normal users expose `admin: false`.
- Postgres store test verifies roles persist, deduplicate, and sort deterministically.

