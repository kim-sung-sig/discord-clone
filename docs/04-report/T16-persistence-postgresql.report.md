# T16 Persistence/PostgreSQL Migration Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T16 Persistence/PostgreSQL Migration

## Completed

- Added PostgreSQL profile with manual `DataSource` and Flyway migration bootstrap.
- Added core schema migrations for users, auth, guild/channel/role, messages, and invites.
- Added auth PostgreSQL store and kept default in-memory profile behavior.
- Added guild/channel/role snapshot persistence for the `postgres` profile.
- Added message snapshot persistence, including edit history, pin/delete state, and string mention tokens.
- Added invite snapshot persistence, including accepted members and deletion timestamp.
- Added PostgreSQL integration tests for bootstrap, auth, guild, message, and invite persistence.
- Ran runtime API smoke against a live Spring Boot server using the `postgres` profile.

## Commits

- `c543bab docs: design T16 persistence postgresql`
- `ad11f93 feat: add postgresql migration baseline`
- `ff8f1ea feat: add auth postgres store`
- `b682f1d feat: add guild postgres snapshots`
- `f275563 feat: add message postgres snapshots`
- `920ec65 feat: add invite postgres snapshots`

## QA Evidence

- `.\gradlew.bat :backend:boot:test --tests com.example.discord.persistence.PersistenceBootstrapTest --rerun-tasks`: PASS
- `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.PostgresAuthStoreTest --rerun-tasks`: PASS
- `.\gradlew.bat :backend:boot:test --tests com.example.discord.guild.PostgresGuildServiceTest --rerun-tasks`: PASS
- `.\gradlew.bat :backend:boot:test --tests com.example.discord.message.PostgresMessageServiceTest --rerun-tasks`: PASS
- `.\gradlew.bat :backend:boot:test --tests com.example.discord.invite.PostgresInviteServiceTest --rerun-tasks`: PASS
- `.\gradlew.bat test`: PASS
- `SPRING_PROFILES_ACTIVE=postgres` server startup: PASS, Flyway validated 4 migrations and schema version 4 was current
- `powershell -NoProfile -ExecutionPolicy Bypass -File qa\api-smoke.ps1 -BaseUrl http://127.0.0.1:8080`: PASS, `API_SMOKE_PASS`

## Outcome

T16 meets the persistence baseline success criteria for auth, guild/channel, message, and invite critical paths. The application can now run the runtime API smoke against PostgreSQL while preserving the fast in-memory default profile for existing tests.

## Next Task Candidate

Proceed to the recommended next item: `T23 Frontend Real API Integration Stabilization`.
