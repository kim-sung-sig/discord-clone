$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'production-profile-guard-smoke.ps1'
$workflowPath = Join-Path $repoRoot '.github/workflows/ci.yml'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $scriptPath) 'qa/production-profile-guard-smoke.ps1 is missing'
Assert (Test-Path $workflowPath) '.github/workflows/ci.yml is missing'

$scriptContent = Get-Content -Path $scriptPath -Raw
$workflowContent = Get-Content -Path $workflowPath -Raw

$requiredScriptSnippets = @(
  ':backend:boot:bootRun',
  '--spring.profiles.active=production',
  'production-like runtime profiles require postgres to avoid in-memory persistence defaults',
  'PRODUCTION_PROFILE_GUARD_SMOKE_PASS',
  'qa/artifacts/production-profile-guard',
  'bootrun.log'
)

foreach ($snippet in $requiredScriptSnippets) {
  Assert ($scriptContent.Contains($snippet)) "production profile guard smoke is missing required snippet: $snippet"
}

$requiredWorkflowSnippets = @(
  'qa-production-profile-guard:',
  'Production profile guard smoke contract',
  'pwsh qa/production-profile-guard-smoke.contract.ps1',
  'Production profile guard smoke',
  'pwsh qa/production-profile-guard-smoke.ps1',
  'production-profile-guard-artifacts',
  'qa/artifacts/production-profile-guard'
)

foreach ($snippet in $requiredWorkflowSnippets) {
  Assert ($workflowContent.Contains($snippet)) "CI workflow is missing production profile guard snippet: $snippet"
}

Write-Output 'PRODUCTION_PROFILE_GUARD_SMOKE_CONTRACT_PASS'
