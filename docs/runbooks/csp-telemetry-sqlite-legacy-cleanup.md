# CSP Telemetry SQLite Legacy Cleanup Runbook

Date: 2026-05-20
Scope: Archive or delete legacy local SQLite CSP telemetry files after migrating to Postgres telemetry.

## Purpose

T106 allowed local durable CSP telemetry through `NUXT_CSP_TELEMETRY_SQLITE_PATH`. T109 replaced the active durable backend with `NUXT_CSP_TELEMETRY_POSTGRES_URL`. Use this runbook to handle old `.sqlite` files left on developer machines, single-node hosts, or archived deployments.

Do not import old SQLite telemetry into Postgres. The old files may contain historical single-node telemetry with different retention windows, and importing them can distort current alert/retention metrics.

## Node runtime compatibility

Postgres is the supported durable backend for CSP telemetry. If `NUXT_CSP_TELEMETRY_POSTGRES_URL` is unset, the active application path falls back to in-memory telemetry.

SQLite is legacy-only. `NUXT_CSP_TELEMETRY_SQLITE_PATH` and `node:sqlite` must remain absent from active runtime config and active application imports. Node 24 can provide a `node:sqlite` module, but this project must not depend on that runtime module for CSP telemetry after the Postgres migration.

Do not use SQLite for production or multi-instance dashboard telemetry. Restoring SQLite support requires an explicit exception, a local-only or isolated analysis workspace, and a fresh compatibility/security review before any code path is reintroduced.

## Pre-check

- Confirm the deployment now uses `NUXT_CSP_TELEMETRY_POSTGRES_URL`.
- Confirm `NUXT_CSP_TELEMETRY_SQLITE_PATH` is unset in active runtime environments.
- Confirm the `/security` dashboard shows Postgres or expected current storage health.
- Confirm no rollback plan depends on the old SQLite file.

## Locate Files

Common legacy values:

```env
NUXT_CSP_TELEMETRY_SQLITE_PATH=./data/csp-telemetry.sqlite
```

PowerShell search:

```powershell
Get-ChildItem -Path . -Recurse -File -Include *.sqlite,*.sqlite3 |
  Where-Object { $_.Name -like '*csp*telemetry*' -or $_.FullName -like '*csp*telemetry*' }
```

Bash search:

```bash
find . -type f \( -name '*.sqlite' -o -name '*.sqlite3' \) | grep -i 'csp.*telemetry'
```

## Archive

Archive if the file may be needed for incident review or release evidence.

PowerShell:

```powershell
$source = '<path-to-csp-telemetry.sqlite>'
$archiveDir = 'archive/csp-telemetry-sqlite'
New-Item -ItemType Directory -Force -Path $archiveDir | Out-Null
$hash = (Get-FileHash -Algorithm SHA256 -Path $source).Hash.ToLowerInvariant()
Copy-Item -LiteralPath $source -Destination "$archiveDir/csp-telemetry-$hash.sqlite"
"sha256  $hash  $source" | Set-Content -Path "$archiveDir/csp-telemetry-$hash.sha256.txt"
```

Bash:

```bash
source='<path-to-csp-telemetry.sqlite>'
archive_dir='archive/csp-telemetry-sqlite'
mkdir -p "$archive_dir"
hash="$(sha256sum "$source" | awk '{print $1}')"
cp "$source" "$archive_dir/csp-telemetry-$hash.sqlite"
printf 'sha256  %s  %s\n' "$hash" "$source" > "$archive_dir/csp-telemetry-$hash.sha256.txt"
```

## Delete

Delete only after Postgres telemetry has been verified and any required archive has been created.

PowerShell:

```powershell
Remove-Item -LiteralPath '<path-to-csp-telemetry.sqlite>'
```

Bash:

```bash
rm -- '<path-to-csp-telemetry.sqlite>'
```

## Verification

- `NUXT_CSP_TELEMETRY_SQLITE_PATH` is absent from active runtime config.
- `NUXT_CSP_TELEMETRY_POSTGRES_URL` is configured where durable telemetry is required.
- `/security` telemetry storage health is `Postgres ready` or the expected current backend.
- The old `.sqlite` path no longer exists, or the file has been archived with a recorded `sha256`.

## Rollback

Rollback should normally mean disabling Postgres telemetry by unsetting `NUXT_CSP_TELEMETRY_POSTGRES_URL` and returning to in-memory telemetry. Restoring SQLite telemetry is legacy-only and should require an explicit decision because `node:sqlite` support was removed from the active application path after T109.

If an archive must be inspected, copy it to an isolated analysis workspace and keep the original archived file immutable.
