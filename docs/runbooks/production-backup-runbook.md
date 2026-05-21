# Production Backup And PITR Runbook

Status: Active
Owner: Platform/Operations
Applies to: Production PostgreSQL backing the Discord clone runtime

## Purpose

This runbook defines how operators request, verify, restore, and audit production database backups. The local drill in `qa/migration-drill.ps1` proves PostgreSQL dump/restore mechanics, while production recovery must use provider-managed backups, WAL archiving, and point-in-time recovery controls.

## Recovery objectives

- RPO: define the maximum acceptable committed-data loss in the change ticket before enabling or changing production backup policy.
- RTO: define the maximum acceptable time to restore service before the incident commander approves a recovery path.
- PITR: production PostgreSQL must have point-in-time recovery enabled through WAL retention or provider-managed equivalent.
- Retention: keep daily backups and WAL/PITR history according to the environment data retention policy and legal hold requirements.

## Roles and approval

- Incident commander: owns the recovery decision, timeline, and communication.
- Database operator: runs provider console or CLI recovery actions.
- Application operator: validates backend, web, gateway, and smoke checks after restore.
- SECURITY_ADMIN: reviews access, audit, and sensitive-data handling.
- Change ticket: required for planned backup policy changes, restore drills, and any production restore.

Do not start a production restore from chat approval alone. The change ticket or incident record must name the source environment, restore target, requested timestamp, RPO/RTO target, and approver.

## Secret and artifact handling

- Do not paste DATABASE_URL, tokens, passwords, connection strings, raw dump contents, WAL files, or provider console screenshots containing secrets into chat.
- Do not commit dump files, WAL archives, generated restore artifacts, `.env` files, or provider export bundles.
- Store backup evidence only in the approved ticket/evidence system.
- Redact hostnames, user names, and credential material unless the evidence system explicitly allows them.
- If a provider export is required, encrypt it with the approved KMS key before transfer.

## Backup policy checklist

1. Confirm managed backup is enabled for production PostgreSQL.
2. Confirm WAL archiving or provider PITR is enabled.
3. Confirm the configured PITR window meets the RPO.
4. Confirm backup retention meets policy and legal hold requirements.
5. Confirm backup encryption at rest uses the approved KMS key.
6. Confirm backup access is limited to named operators and audited.
7. Confirm cross-region or cross-zone backup copy policy is documented for the environment.
8. Record the latest successful backup timestamp and provider backup identifier in the change ticket.

## Provider mapping

### AWS RDS or Aurora PostgreSQL

- Use automated backups with PITR enabled.
- Verify backup retention period and latest restorable time.
- Use snapshots only for base backup evidence; use PITR for timestamp restore.
- Restore to a new instance or cluster first. Do not overwrite production in place.

### GCP Cloud SQL for PostgreSQL

- Use automated backups and point-in-time recovery.
- Verify transaction log retention and latest recovery time.
- Restore to a new Cloud SQL instance first.
- Validate private networking and IAM bindings before application cutover.

### Azure Database for PostgreSQL

- Use automated backups and point-in-time restore.
- Verify backup retention days and geo-redundant backup setting where required.
- Restore to a new server first.
- Validate firewall, private endpoint, and identity bindings before application cutover.

## Planned restore drill

Use this path for quarterly or release-readiness drills.

1. Open a change ticket with RPO, RTO, requested restore timestamp, operator names, and rollback plan.
2. Select the latest backup or PITR timestamp that satisfies the drill objective.
3. Restore to a new isolated restore target.
4. Confirm the restore target cannot send email, webhooks, push notifications, or external callbacks.
5. Run schema migration validation against the restore target.
6. Run `qa/migration-drill.ps1` in a non-production environment to keep local restore mechanics healthy.
7. Confirm the local drill reports `snapshot_hash_comparison=PASS`.
8. For provider restore targets, compare provider checksum or table-level row-count/hash evidence where the provider tooling supports it.
9. Run API smoke against the restore target only after data comparison.
10. Record backup identifier, restore target, checksum evidence, API smoke result, and cleanup timestamp in the change ticket.
11. Delete the restore target after evidence is captured unless the ticket declares a legal hold.

## Incident restore

Use this path only when production data loss, corruption, destructive migration, or unrecoverable environment failure is declared.

1. Incident commander freezes writes or places the service in maintenance mode when needed to prevent further damage.
2. Identify the last known good timestamp and document expected data loss against the RPO.
3. Restore to a new restore target using PITR or the selected managed backup.
4. Validate the restored target before application cutover.
5. Rotate credentials if any restore operator had temporary elevated access.
6. Cut over application configuration only after explicit incident commander approval.
7. Keep the old production database read-only until the incident commander approves disposal.

## Post-restore validation

Run these checks before declaring recovery complete:

- Provider restore job completed successfully.
- Provider checksum, backup identifier, or table-level hash evidence is recorded.
- Schema migration validation completed.
- API smoke completed against the restore target.
- Authentication, guild, channel, message, invite, and gateway-critical paths were reviewed.
- `snapshot_hash_comparison=PASS` is available from the latest local drill or equivalent provider-level row-count/hash evidence.
- SECURITY_ADMIN reviewed global admin audit evidence and dashboard access posture.
- Application logs show no production secret leakage.
- Monitoring confirms database connections, error rate, and latency returned to acceptable levels.

## Abort criteria

Abort or pause the restore when any condition is true:

- Restore target is the current production database.
- Restore timestamp is not approved in the incident or change ticket.
- RPO/RTO impact is unknown or not communicated.
- Provider reports incomplete backup, missing WAL, failed checksum, or failed restore job.
- Restore target can send outbound notifications before isolation is confirmed.
- Required operator or SECURITY_ADMIN approval is missing.
- Validation finds data mismatch without an approved risk acceptance.

## Cleanup

- Remove temporary restore targets after the retention period in the ticket.
- Revoke temporary database/operator access.
- Attach redacted evidence to the ticket.
- Record exact backup identifier, restore timestamp, restore target, checksum status, API smoke result, and final decision.
- Schedule a follow-up task for every manual step that caused delay or uncertainty.

## Local drill reference

The local QA drill remains the engineering safety net for backup/restore mechanics:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File qa\migration-drill.ps1 `
  -SourceJdbcUrl '<local source jdbc url>' `
  -TargetJdbcUrl '<local restore target jdbc url>' `
  -PostgresCliContainer '<postgres cli container>' `
  -BackendUrl 'http://127.0.0.1:<local port>' `
  -BackendStartupTimeoutSeconds 180
```

The expected local evidence includes:

- `source-snapshot-hashes.tsv`
- `restored-snapshot-hashes.tsv`
- `snapshot-hash-comparison.txt`
- `restore-drill-summary.md`
- `RESTORE_DRILL_PASS`
- `snapshot_hash_comparison=PASS`
