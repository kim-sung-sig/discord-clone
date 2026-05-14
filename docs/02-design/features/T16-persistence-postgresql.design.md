# T16 Persistence/PostgreSQL Migration Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T16 Persistence/PostgreSQL Migration

## Architecture Decision

T16 starts with a PostgreSQL-backed persistence baseline, then migrates critical state incrementally. The current in-memory services remain available for fast unit tests while persistence-backed adapters are introduced behind Spring profile/configuration boundaries.

## Flyway vs Liquibase

Decision: Flyway.

Reasons:

- The first migration goal is straightforward DDL: tables, indexes, constraints, and enum-like checks.
- Spring Boot integrates Flyway with minimal configuration and predictable startup behavior.
- SQL-first migrations make PostgreSQL-specific constraints explicit.
- Liquibase is stronger for complex multi-database/change-log workflows, but this project targets PostgreSQL as the production data store.

## Testcontainers vs Local PostgreSQL

Decision: local PostgreSQL profile first, Testcontainers later.

Reasons:

- The user already provided the local DB contract: host port `5432`, user `dev_user`, password `dev_password`.
- Current environment has restricted network access; adding Testcontainers image pulls can slow or block iteration.
- A local profile lets `qa/api-smoke.ps1` validate the same runtime service path used during manual review.

Future task:

- Add Testcontainers once image availability and CI Docker policy are stable.

## Profiles

- `default`: existing in-memory services for fast tests and no external dependency.
- `postgres`: PostgreSQL datasource, Flyway migrations, persistence-backed adapters for migrated domains.

Default must remain lightweight. The `postgres` profile is opt-in until all critical domains are migrated.

## Dependency Plan

Add to `backend/boot`:

- `spring-boot-starter-jdbc`
- `org.flywaydb:flyway-core`
- `org.postgresql:postgresql`

Avoid Spring Data JPA for T16. The project has explicit domain services and value objects; JDBC repositories keep the mapping visible and avoid premature ORM coupling.

## Repository Boundary

### Auth

Port:

- `AuthAccountRepository`

Responsibilities:

- insert account with unique email.
- lookup by email.
- lookup profile by user id.
- store/revoke access sessions if token persistence is included in the slice.

### Guild/Channel/Role

Port:

- `GuildRepository`

Responsibilities:

- persist guilds, channels, roles, member roles, permission overwrites.
- enforce unique role/channel identity where domain requires it.
- load aggregate snapshots for permission evaluation.

### Message

Port:

- `MessageRepository`

Responsibilities:

- persist messages, edit history, deletion tombstones, pinned flag, mentions.
- cursor pagination by monotonic sequence/id.
- support channel-scoped search baseline.

### Invite

Port:

- `InviteRepository`

Responsibilities:

- persist invite code, limits, usage count, deleted flag.
- enforce code uniqueness.
- update use count atomically.

## Migration Baseline

Initial SQL files should create:

- `users`
- `auth_accounts`
- `auth_sessions`
- `guilds`
- `guild_members`
- `guild_roles`
- `guild_member_roles`
- `channels`
- `channel_role_overwrites`
- `messages`
- `message_mentions`
- `message_edits`
- `invites`
- `invite_role_grants`

The schema should prefer UUID primary keys, `created_at` / `updated_at` timestamps, and explicit foreign keys.

## Runtime Configuration

`application-postgres.yml`:

- `spring.datasource.url=jdbc:postgresql://localhost:5432/discord`
- `spring.datasource.username=dev_user`
- `spring.datasource.password=dev_password`
- `spring.flyway.enabled=true`

If the `discord` database does not exist, local setup must create it before boot. In this environment the existing Docker/PostgreSQL instance is assumed to be available on port `5432`.

## Implementation Order

1. Add Flyway/JDBC dependencies and postgres profile.
2. Add baseline migration and migration smoke test.
3. Add auth repository port/adapter.
4. Add guild/channel repository port/adapter.
5. Add message repository port/adapter.
6. Add invite repository port/adapter.
7. Run `qa/api-smoke.ps1` against `postgres` profile.

## QA Strategy

- Keep existing unit tests on in-memory services.
- Add persistence integration tests under `backend/boot/src/test/java/com/example/discord/persistence`.
- Use local profile tests only when PostgreSQL is reachable.
- Runtime QA command:

```powershell
$env:SPRING_PROFILES_ACTIVE='postgres'
.\gradlew.bat :backend:boot:bootRun
powershell -NoProfile -ExecutionPolicy Bypass -File qa\api-smoke.ps1
```

## Risks

- Full migration of all domain modules is too large for one commit. T16 must stay incremental.
- Local PostgreSQL availability can make tests environment-sensitive. Tests should clearly skip or fail with an actionable message if the DB is unavailable.
- JDBC mapping can duplicate domain reconstruction logic unless repository boundaries remain narrow.
