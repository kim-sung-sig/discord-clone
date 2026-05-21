# T55 Restore Snapshot Hash Comparison Plan

Date: 2026-05-21
Status: Completed

## Problem

T39 proved that a local PostgreSQL backup can be restored and pass API smoke, but the post-restore smoke creates new data. It did not prove that the pre-existing seeded rows in the source database survived backup/restore unchanged.

## Scope

- Add a source snapshot hash before `pg_dump`.
- Add a restored target snapshot hash immediately after `pg_restore` and before post-restore API smoke.
- Compare row counts and content hashes for public application tables.
- Write secret-safe evidence artifacts without dumping row contents.
- Keep the existing source/target safety guards and local-only defaults.

## Out Of Scope

- Creating the target restore database automatically. That remains T56.
- Process-tree cleanup for leftover backend child processes. That remains T57.
- Production PITR/cloud backup runbook. That remains T58.

## Acceptance Criteria

- The migration drill contract fails before hash comparison snippets exist.
- The contract passes after implementation.
- A real Docker-backed drill produces `source-snapshot-hashes.tsv`, `restored-snapshot-hashes.tsv`, and `snapshot_hash_comparison=PASS`.
- The restore drill still runs API smoke after the hash comparison.

## Wiki Used

- `C:\tmp\ObsidianVaults\discord-llm-wiki\index.md`
- `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\QA Infra Operations.md`
- `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\Current Roadmap And Risks.md`
