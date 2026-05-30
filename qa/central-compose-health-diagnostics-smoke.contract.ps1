$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$health = Join-Path $repoRoot 'qa/central-compose-health.ps1'
$smoke = Join-Path $repoRoot 'qa/central-compose-health-diagnostics-smoke.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $health) 'central compose health script is missing'
Assert (Test-Path $smoke) 'central compose health diagnostics smoke script is missing'

$healthText = Get-Content -Path $health -Raw
Assert ($healthText.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE')) 'health script must expose a controlled diagnostic smoke mode'
Assert ($healthText.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE_FAILURE')) 'health script must emit a stable forced-failure marker'

$smokeText = Get-Content -Path $smoke -Raw
Assert ($smokeText.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE')) 'diagnostic smoke must enable controlled diagnostic mode'
Assert ($smokeText.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTICS')) 'diagnostic smoke must assert diagnostics marker'
Assert ($smokeText.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE_FAILURE')) 'diagnostic smoke must assert forced-failure marker'
Assert ($smokeText.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTICS_SMOKE_PASS')) 'diagnostic smoke must emit a stable pass marker'

Write-Output 'CENTRAL_COMPOSE_HEALTH_DIAGNOSTICS_SMOKE_CONTRACT_PASS'
