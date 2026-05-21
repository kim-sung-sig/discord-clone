# T58 Production Backup Runbook Design

Date: 2026-05-21
Status: Completed

## Design

The runbook is provider-neutral at the decision layer and provider-specific at the execution checklist layer. It covers AWS RDS/Aurora, GCP Cloud SQL, and Azure Database for PostgreSQL without hard-coding environment secrets or a deployment provider.

## Contract

`qa/production-backup-runbook.contract.ps1` checks:

- runbook existence,
- recovery objective terms,
- PITR/WAL/base backup/managed backup coverage,
- approval roles and change-ticket requirements,
- secret/artifact handling,
- local drill evidence linkage,
- provider names,
- post-restore validation,
- forbidden credential examples.

## Security Boundary

The runbook explicitly forbids pasting `DATABASE_URL`, passwords, raw dumps, WAL files, and provider screenshots containing secrets into chat or source control.
