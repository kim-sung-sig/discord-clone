# T55 Restore Snapshot Hash Comparison Design

Date: 2026-05-21
Status: Completed

## Snapshot Model

The drill writes a TSV file with:

- `table`
- `row_count`
- `row_hash`

The hash is computed in PostgreSQL from `row_to_json(row)::text` values sorted by their JSON text. This avoids exposing row contents while still detecting changed, missing, or extra restored rows.

## Scope Of Tables

The snapshot includes `public` regular tables and excludes `flyway_schema_history`. Migration metadata still runs through backend startup/Flyway validation, while restored product data is checked by the snapshot comparison.

## Drill Order

1. Run migration guard.
2. Start or reuse source backend.
3. Seed source through API smoke.
4. Stop source backend when owned by the drill.
5. Write source snapshot hash.
6. Run `qa/db-backup.ps1`.
7. Run `qa/db-restore.ps1`.
8. Write restored target snapshot hash.
9. Compare source/restored hash files.
10. Run restored API smoke.

## Security Notes

- Snapshot artifacts contain redacted JDBC URLs, table names, counts, and hashes only.
- PostgreSQL passwords are passed through `PGPASSWORD` or Docker environment, matching the existing local drill behavior.
- Production-like URLs remain rejected unless explicitly overridden by the existing approved flag.
