$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'real-backend-e2e.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $scriptPath) 'qa/real-backend-e2e.ps1 is missing'

$content = Get-Content -Path $scriptPath -Raw
$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref] $tokens, [ref] $parseErrors) | Out-Null
Assert ($parseErrors.Count -eq 0) "qa/real-backend-e2e.ps1 has parse errors: $($parseErrors | ConvertTo-Json -Compress)"

$requiredSnippets = @(
  "`$BackendUrl = 'http://127.0.0.1:8080'",
  "`$PostgresJdbcUrl = 'jdbc:postgresql://127.0.0.1:5432/discord'",
  "`$PostgresUser = 'dev_user'",
  "`$PostgresPassword = 'dev_password'",
  "`$ArtifactsDir = 'qa/artifacts/real-backend-e2e'",
  "`$BackendStartupTimeoutSeconds = 120",
  '[switch] $SkipServiceStart',
  '/actuator/health',
  ':backend:boot:bootRun',
  'qa/api-smoke.ps1',
  'REAL_BACKEND_E2E',
  'REAL_BACKEND_BASE_URL',
  'NUXT_PUBLIC_API_BASE_URL',
  'tests/e2e/real-backend.spec.ts',
  'Stop-Process -Id $backendProcess.Id'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "qa/real-backend-e2e.ps1 is missing required snippet: $snippet"
}

Write-Output 'REAL_BACKEND_E2E_CONTRACT_PASS'
