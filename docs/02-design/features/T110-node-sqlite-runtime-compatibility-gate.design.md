# T110 Node SQLite Runtime Compatibility Gate Design

Date: 2026-05-21

## Design

The gate remains a lightweight PowerShell contract because this behavior is mostly documentation and configuration boundary rather than product runtime logic.

The contract checks three surfaces:

- `docs/runbooks/csp-telemetry-sqlite-legacy-cleanup.md` must state the Node runtime compatibility policy.
- `apps/web/server/utils/csp-telemetry-store.ts` must not reference legacy SQLite runtime snippets.
- `.env.example` must not expose `NUXT_CSP_TELEMETRY_SQLITE_PATH`.

## Runtime Policy

Postgres is the only supported durable backend for CSP telemetry. If Postgres is unset, the app uses in-memory telemetry. SQLite stays legacy-only for old local files and incident evidence handling.

## Security Rationale

Keeping SQLite out of active runtime avoids local file persistence ambiguity, multi-instance inconsistency, and accidental dependency on `node:sqlite` availability. Old `.sqlite` files are handled by archive/delete guidance rather than by restoring app support.
