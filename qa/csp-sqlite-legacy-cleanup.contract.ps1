$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$runbookPath = Join-Path $repoRoot 'docs/runbooks/csp-telemetry-sqlite-legacy-cleanup.md'
$storePath = Join-Path $repoRoot 'apps/web/server/utils/csp-telemetry-store.ts'
$envExamplePath = Join-Path $repoRoot '.env.example'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $runbookPath) 'CSP telemetry SQLite legacy cleanup runbook is missing'
Assert (Test-Path $storePath) 'CSP telemetry store implementation is missing'
Assert (Test-Path $envExamplePath) '.env.example is missing'

$content = Get-Content -Path $runbookPath -Raw
$requiredSnippets = @(
  'NUXT_CSP_TELEMETRY_SQLITE_PATH',
  'NUXT_CSP_TELEMETRY_POSTGRES_URL',
  '.sqlite',
  'Archive',
  'sha256',
  'Delete',
  'Do not import old SQLite telemetry into Postgres',
  'Node runtime compatibility',
  'Node 24',
  'node:sqlite',
  'legacy-only',
  'must remain absent from active runtime config',
  'Do not use SQLite for production or multi-instance dashboard telemetry',
  'Postgres is the supported durable backend',
  'Verification',
  'Rollback'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "CSP SQLite cleanup runbook is missing required snippet: $snippet"
}

$storeContent = Get-Content -Path $storePath -Raw
$envExampleContent = Get-Content -Path $envExamplePath -Raw

$forbiddenStoreSnippets = @(
  'NUXT_CSP_TELEMETRY_SQLITE_PATH',
  'node:sqlite',
  'SqliteCspTelemetryStore'
)

foreach ($snippet in $forbiddenStoreSnippets) {
  Assert (-not $storeContent.Contains($snippet)) "CSP telemetry active runtime must not contain legacy SQLite snippet: $snippet"
}

Assert (-not $envExampleContent.Contains('NUXT_CSP_TELEMETRY_SQLITE_PATH')) '.env.example must not advertise legacy SQLite runtime config'

Write-Output 'CSP_SQLITE_LEGACY_CLEANUP_CONTRACT_PASS'
