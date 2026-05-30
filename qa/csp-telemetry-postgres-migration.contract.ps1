$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$migrationPath = Join-Path $repoRoot 'apps/web/server/database/migrations/001_csp_telemetry_postgres.sql'
$runbookPath = Join-Path $repoRoot 'docs/runbooks/csp-telemetry-postgres-migration.md'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $migrationPath) 'CSP telemetry Postgres migration SQL is missing'
Assert (Test-Path $runbookPath) 'CSP telemetry Postgres migration runbook is missing'

$migration = Get-Content -Path $migrationPath -Raw
$migrationSnippets = @(
  'BEGIN;',
  'CREATE TABLE IF NOT EXISTS csp_telemetry',
  'CREATE INDEX IF NOT EXISTS idx_csp_telemetry_received_at',
  'CREATE INDEX IF NOT EXISTS idx_csp_telemetry_effective_directive',
  'CREATE TABLE IF NOT EXISTS csp_telemetry_retention_metrics',
  'CREATE TABLE IF NOT EXISTS csp_rate_limit_telemetry',
  'CREATE INDEX IF NOT EXISTS idx_csp_rate_limit_telemetry_received_at',
  'CREATE TABLE IF NOT EXISTS csp_alert_transitions',
  'CREATE INDEX IF NOT EXISTS idx_csp_alert_transitions_observed_at',
  'COMMIT;'
)

foreach ($snippet in $migrationSnippets) {
  Assert ($migration.Contains($snippet)) "CSP telemetry migration is missing required snippet: $snippet"
}

$runbook = Get-Content -Path $runbookPath -Raw
$runbookSnippets = @(
  'NUXT_CSP_TELEMETRY_POSTGRES_URL',
  '001_csp_telemetry_postgres.sql',
  'psql',
  'csp_telemetry',
  'csp_rate_limit_telemetry',
  'csp_alert_transitions',
  'Verification',
  'Rollback'
)

foreach ($snippet in $runbookSnippets) {
  Assert ($runbook.Contains($snippet)) "CSP telemetry migration runbook is missing required snippet: $snippet"
}

Write-Output 'CSP_TELEMETRY_POSTGRES_MIGRATION_CONTRACT_PASS'
