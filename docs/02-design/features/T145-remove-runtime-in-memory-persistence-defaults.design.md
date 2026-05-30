# T145 Remove Runtime In-Memory Persistence Defaults Design

Date: 2026-05-20
Slice: T145 Remove Runtime In-Memory Persistence Defaults

## Profile Contract

Production-like profiles:

- `production`
- `admin-cli`

These profiles require `postgres`.

## Runtime Guard

`RuntimeResourceProfileGuard` validates active Spring profiles during context startup.

If `production` or `admin-cli` is active and `postgres` is absent, startup fails with:

`production-like runtime profiles require postgres to avoid in-memory persistence defaults`

## Bean Scope Changes

In-memory persistence beans are excluded from production-like profiles:

- `InMemoryAuthStore`
- `GuildConfiguration.inMemoryGuildService`
- `MessageConfiguration.inMemoryMessageService`
- `InviteConfiguration.inMemoryInviteService`

## Local/Test Behavior

No-profile local runs and `test` profile runs continue to use in-memory persistence. This keeps controller/unit tests fast while preventing production-like accidental data loss.
