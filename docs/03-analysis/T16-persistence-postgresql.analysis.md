# T16 Persistence/PostgreSQL Migration Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T16 Persistence/PostgreSQL Migration

## Verification Summary

| Area | Command | Result |
| --- | --- | --- |
| PostgreSQL bootstrap | `.\gradlew.bat :backend:boot:test --tests com.example.discord.persistence.PersistenceBootstrapTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Auth persistence | `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.PostgresAuthStoreTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Guild persistence | `.\gradlew.bat :backend:boot:test --tests com.example.discord.guild.PostgresGuildServiceTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Message persistence | `.\gradlew.bat :backend:boot:test --tests com.example.discord.message.PostgresMessageServiceTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Invite persistence | `.\gradlew.bat :backend:boot:test --tests com.example.discord.invite.PostgresInviteServiceTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Domain/API regressions | Auth, Guild, Message, Invite targeted regression commands | PASS, BUILD SUCCESSFUL |
| Backend full | `.\gradlew.bat test` | PASS, BUILD SUCCESSFUL |
| Runtime postgres smoke | `powershell -NoProfile -ExecutionPolicy Bypass -File qa\api-smoke.ps1 -BaseUrl http://127.0.0.1:8080` with `SPRING_PROFILES_ACTIVE=postgres` | PASS, `API_SMOKE_PASS` |

## Success Criteria Check

- PostgreSQL schema migration baseline exists: PASS with Flyway `V1` through `V4`.
- Signup/login/profile survives PostgreSQL-backed service path: PASS via `JdbcAuthStore` and API smoke.
- Guild/channel/role membership critical path persists: PASS via `PersistentGuildService` snapshot reload test and API smoke.
- Message create/edit/pin/mentions critical path persists: PASS via `PersistentMessageService` reload test and API smoke.
- Invite create/accept/delete critical path persists: PASS via `PersistentInviteService` reload test and API smoke.
- Runtime smoke can run against persistence profile: PASS on local PostgreSQL `127.0.0.1:15432/discord`.
- Default in-memory tests remain available: PASS via full `.\gradlew.bat test`.

## Design Match

- Default profile remains in-memory; `postgres` profile is opt-in.
- Flyway is the migration runner and is explicitly completed before JDBC repositories initialize through `@DependsOn("postgresFlyway")`.
- Auth uses a direct store port with PostgreSQL implementation under the `postgres` profile.
- Guild, message, and invite keep existing in-memory domain behavior and add PostgreSQL snapshot persistence around mutating operations.
- Message mention persistence adds `message_mention_tokens` because the current domain stores mention tokens as strings, not only UUIDs.
- Invite persistence adds `invite_acceptances` and `deleted_at` to preserve existing accepted-member and deletion semantics.

## Residual Risks

- Snapshot persistence favors low-risk integration over highly concurrent write optimization; high-volume message writes should later move to append/update repositories.
- Local PostgreSQL tests depend on the developer DB being reachable at `127.0.0.1:15432`.
- Flyway warns that PostgreSQL 17.6 is newer than the currently tested Flyway support range; migrations still validated and ran successfully.
- Some non-critical domains remain in-memory under `postgres` profile, including premium, stage, soundboard, audit, and moderation.
