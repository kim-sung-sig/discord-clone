$ErrorActionPreference = 'Stop'

$scriptPaths = @(
  (Join-Path $PSScriptRoot 'db-backup.ps1'),
  (Join-Path $PSScriptRoot 'db-restore.ps1'),
  (Join-Path $PSScriptRoot 'migration-drill.ps1'),
  (Join-Path $PSScriptRoot 'migration-guard.contract.ps1')
)

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

foreach ($scriptPath in $scriptPaths) {
  Assert (Test-Path $scriptPath) "Missing migration drill script: $scriptPath"

  $tokens = $null
  $parseErrors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref] $tokens, [ref] $parseErrors) | Out-Null
  Assert ($parseErrors.Count -eq 0) "$scriptPath has parse errors: $($parseErrors | ConvertTo-Json -Compress)"
}

$backup = Get-Content -Path (Join-Path $PSScriptRoot 'db-backup.ps1') -Raw
$restore = Get-Content -Path (Join-Path $PSScriptRoot 'db-restore.ps1') -Raw
$drill = Get-Content -Path (Join-Path $PSScriptRoot 'migration-drill.ps1') -Raw
$guard = Get-Content -Path (Join-Path $PSScriptRoot 'migration-guard.contract.ps1') -Raw
$common = Get-Content -Path (Join-Path $PSScriptRoot 'db-drill-common.ps1') -Raw

$requiredBackupSnippets = @(
  '[string] $SourceJdbcUrl',
  '[switch] $AllowNonLocal',
  'Assert-SafeJdbcUrl $SourceJdbcUrl',
  'pg_dump',
  'PGPASSWORD',
  'redacted',
  'qa/artifacts/db-drill'
)

foreach ($snippet in $requiredBackupSnippets) {
  Assert ($backup.Contains($snippet)) "db-backup.ps1 is missing required snippet: $snippet"
}

$requiredRestoreSnippets = @(
  '[string] $TargetJdbcUrl',
  '[string] $SourceJdbcUrl',
  '[switch] $ConfirmCleanTarget',
  '[switch] $EnsureTargetDatabase',
  'Assert-SourceAndTargetDiffer',
  'Ensure-PostgresDatabaseExists',
  'DROP SCHEMA IF EXISTS public CASCADE',
  'CREATE SCHEMA public',
  'pg_restore',
  'refusing to restore into source database'
)

foreach ($snippet in $requiredRestoreSnippets) {
  Assert ($restore.Contains($snippet)) "db-restore.ps1 is missing required snippet: $snippet"
}

$requiredDrillSnippets = @(
  'qa/db-backup.ps1',
  'qa/db-restore.ps1',
  'qa/migration-guard.contract.ps1',
  'qa/api-smoke.ps1',
  '-EnsureTargetDatabase',
  'Write-DatabaseSnapshotHash',
  'Compare-DatabaseSnapshotHashes',
  'source-snapshot-hashes.tsv',
  'restored-snapshot-hashes.tsv',
  'snapshot_hash_comparison=PASS',
  ':backend:boot:bootRun',
  'SPRING_PROFILES_ACTIVE',
  'POSTGRES_JDBC_URL',
  'retention_keep_latest=5',
  'RESTORE_DRILL_PASS'
)

foreach ($snippet in $requiredDrillSnippets) {
  Assert ($drill.Contains($snippet)) "migration-drill.ps1 is missing required snippet: $snippet"
}

$destructivePatterns = @(
  'DROP\s+TABLE',
  'DROP\s+COLUMN',
  'TRUNCATE',
  'DELETE\s+FROM',
  'ALTER\s+TABLE.*RENAME\s+COLUMN'
)

foreach ($pattern in $destructivePatterns) {
  Assert ($guard.Contains($pattern)) "migration-guard.contract.ps1 is missing destructive pattern: $pattern"
}

Assert (($backup + $common).Contains('production') -and ($backup + $common).Contains('prod')) 'db-backup.ps1 must refuse production-like URLs'
Assert (($restore + $common).Contains('production') -and ($restore + $common).Contains('prod')) 'db-restore.ps1 must refuse production-like URLs'
Assert ($drill.Contains('SourceJdbcUrl -eq TargetJdbcUrl')) 'migration-drill.ps1 must explicitly reject identical source and target URLs'

Write-Output 'MIGRATION_DRILL_CONTRACT_PASS'
