$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'dashboard-guard-health-smoke.ps1'
$workflowPath = Join-Path $repoRoot '.github/workflows/ci.yml'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $scriptPath) 'qa/dashboard-guard-health-smoke.ps1 is missing'
Assert (Test-Path $workflowPath) '.github/workflows/ci.yml is missing'

$script = Get-Content -Path $scriptPath -Raw
$scriptSnippets = @(
  '/api/security/dashboard-guard-health',
  'Invoke-WebRequest',
  'NODE_ENV',
  'production',
  'NUXT_SECURITY_DASHBOARD_TOKEN',
  'fail-closed',
  'guard-health.json',
  'DASHBOARD_GUARD_HEALTH_SMOKE_PASS'
)

foreach ($snippet in $scriptSnippets) {
  Assert ($script.Contains($snippet)) "dashboard guard health smoke is missing required snippet: $snippet"
}

$workflow = Get-Content -Path $workflowPath -Raw
$workflowSnippets = @(
  'qa-dashboard-guard-health:',
  'actions/setup-node@v4',
  'node-version: ''22''',
  'npm ci',
  'pwsh qa/dashboard-guard-health-smoke.contract.ps1',
  'pwsh qa/dashboard-guard-health-smoke.ps1',
  'NUXT_SECURITY_DASHBOARD_TOKEN: ci-dashboard-guard-token',
  'DASHBOARD_GUARD_HEALTH_ARTIFACT_DIR: qa/artifacts/dashboard-guard-health/ci',
  'dashboard-guard-health-artifacts',
  'qa/artifacts/dashboard-guard-health'
)

foreach ($snippet in $workflowSnippets) {
  Assert ($workflow.Contains($snippet)) "CI workflow is missing dashboard guard health snippet: $snippet"
}

Write-Output 'DASHBOARD_GUARD_HEALTH_SMOKE_CONTRACT_PASS'
