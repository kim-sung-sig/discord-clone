# T58 Production Backup Runbook Plan

Date: 2026-05-21
Status: Completed

## Problem

T39 and T55 prove local PostgreSQL backup/restore mechanics, but they do not define production PITR, managed backup ownership, approvals, secret handling, or provider-specific restore procedures.

## Scope

- Add a production backup and PITR runbook.
- Add a contract test that prevents the runbook from losing required operational/security sections.
- Link production restore validation back to the local `qa/migration-drill.ps1` and `snapshot_hash_comparison=PASS` evidence.

## Out Of Scope

- Running production backup commands.
- Selecting a specific cloud provider for deployment.
- Automating restore target lifecycle. That remains T56.

## Acceptance Criteria

- Contract fails when the runbook is absent.
- Contract passes after the runbook includes RPO/RTO/PITR, provider mapping, approvals, secret handling, validation, and abort criteria.
- Runbook contains no sample production credentials.

## Wiki Used

- `C:\tmp\ObsidianVaults\discord-llm-wiki\index.md`
- `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\QA Infra Operations.md`
- `C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\Current Roadmap And Risks.md`
