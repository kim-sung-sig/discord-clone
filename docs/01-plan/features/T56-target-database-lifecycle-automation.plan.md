# T56 Target Database Lifecycle Automation Plan

Date: 2026-05-21
Status: Completed

## Problem

The restore drill still required operators to create the target PostgreSQL database before running `qa/migration-drill.ps1`. That manual precondition slowed drills and made fresh restore target verification brittle.

## Scope

- Add an explicit restore flag that ensures the target database exists before cleaning/restoring schema.
- Wire the migration drill to use the target database lifecycle automation by default.
- Keep source/target URL safety checks and production-like URL rejection.
- Verify against a missing local restore target database.

## Out Of Scope

- Dropping production databases.
- Automated cleanup of restore databases after evidence capture.
- Process-tree cleanup for backend child processes. That remains T57.

## Acceptance Criteria

- Contract fails before target database lifecycle snippets exist.
- Contract passes after implementation.
- A real drill against a missing local target DB creates the DB, restores data, compares snapshot hashes, and passes API smoke.

## Wiki Used

- `C:\tmp\ObsidianVaults\discord-llm-wiki\index.md`
- `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\QA Infra Operations.md`
- `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\Current Roadmap And Risks.md`
