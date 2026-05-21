$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$runbookPath = Join-Path $repoRoot 'docs/runbooks/production-backup-runbook.md'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $runbookPath) 'docs/runbooks/production-backup-runbook.md is missing'

$runbook = Get-Content -Path $runbookPath -Raw

$requiredRunbookSnippets = @(
  'Recovery objectives',
  'RPO',
  'RTO',
  'PITR',
  'WAL',
  'base backup',
  'managed backup',
  'change ticket',
  'incident commander',
  'SECURITY_ADMIN',
  'Do not paste DATABASE_URL',
  'Do not commit dump files',
  'snapshot_hash_comparison=PASS',
  'qa/migration-drill.ps1',
  'restore target',
  'checksum',
  'API smoke',
  'Abort criteria',
  'AWS RDS',
  'GCP Cloud SQL',
  'Azure Database for PostgreSQL',
  'Post-restore validation'
)

foreach ($snippet in $requiredRunbookSnippets) {
  Assert ($runbook.Contains($snippet)) "production backup runbook is missing required snippet: $snippet"
}

$forbiddenRunbookSnippets = @(
  'dev_password@',
  'postgres://dev_user:dev_password',
  'PGPASSWORD=dev_password',
  'paste dump'
)

foreach ($snippet in $forbiddenRunbookSnippets) {
  Assert (-not $runbook.Contains($snippet)) "production backup runbook contains unsafe snippet: $snippet"
}

Write-Output 'PRODUCTION_BACKUP_RUNBOOK_CONTRACT_PASS'
