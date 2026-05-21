# T56 Target Database Lifecycle Automation Design

Date: 2026-05-21
Status: Completed

## Design

`qa/db-restore.ps1` now accepts `-EnsureTargetDatabase`. When enabled, it connects to the PostgreSQL maintenance database and creates the target database only if it does not already exist.

`qa/migration-drill.ps1` always passes `-EnsureTargetDatabase` to the restore step, so local restore drills no longer require pre-created target databases.

## Safety

- The existing source/target equality rejection remains in place.
- `Assert-SafeJdbcUrl` still rejects production-like and non-local URLs unless explicitly approved.
- The automation creates a missing database; it does not drop the whole database.
- Schema cleanup remains guarded by `-ConfirmCleanTarget`.

## Evidence

Restore metadata records whether lifecycle handling was `ensured` or `preexisting`.
