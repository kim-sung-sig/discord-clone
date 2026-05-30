$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$health = Join-Path $repoRoot 'qa/central-compose-health.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

function Get-PowerShellCommand {
  $pwsh = Get-Command pwsh -ErrorAction SilentlyContinue
  if ($null -ne $pwsh) {
    return $pwsh.Source
  }
  return 'powershell'
}

$previousDiagnosticSmoke = $env:CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE
try {
  $env:CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE = 'true'
  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    $output = & (Get-PowerShellCommand) -ExecutionPolicy Bypass -File $health *>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }

  $text = $output -join [Environment]::NewLine
  Assert ($exitCode -ne 0) 'diagnostic smoke must fail the health script through the controlled path'
  Assert ($text.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTICS')) 'diagnostic output must include stable diagnostics marker'
  Assert ($text.Contains('resource=diagnostic-smoke')) 'diagnostic output must include forced diagnostic resource'
  Assert ($text.Contains('port=1')) 'diagnostic output must include forced diagnostic port'
  Assert ($text.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE_FAILURE')) 'diagnostic output must include forced-failure marker'

  Write-Output 'CENTRAL_COMPOSE_HEALTH_DIAGNOSTICS_SMOKE_PASS'
} finally {
  $env:CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE = $previousDiagnosticSmoke
}
