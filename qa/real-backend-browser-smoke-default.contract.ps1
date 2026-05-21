$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$packagePath = Join-Path $repoRoot 'package.json'
$runnerPath = Join-Path $repoRoot 'qa/real-backend-e2e.mjs'
$harnessPath = Join-Path $repoRoot 'qa/real-backend-e2e.ps1'
$specPath = Join-Path $repoRoot 'apps/web/tests/e2e/real-backend.spec.ts'
$workflowPath = Join-Path $repoRoot '.github/workflows/ci.yml'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $packagePath) 'package.json is missing'
Assert (Test-Path $harnessPath) 'qa/real-backend-e2e.ps1 is missing'
Assert (Test-Path $specPath) 'apps/web/tests/e2e/real-backend.spec.ts is missing'
Assert (Test-Path $workflowPath) '.github/workflows/ci.yml is missing'
Assert (Test-Path $runnerPath) 'qa/real-backend-e2e.mjs is missing'

$package = Get-Content -Path $packagePath -Raw | ConvertFrom-Json
Assert ($package.scripts.'e2e:real-backend' -eq 'node qa/real-backend-e2e.mjs') `
  'package.json must expose npm run e2e:real-backend as the default real backend browser smoke gate'

$runner = Get-Content -Path $runnerPath -Raw
$runnerSnippets = @(
  'spawnSync',
  'qa/real-backend-e2e.ps1',
  'process.platform === ''win32''',
  'powershell.exe',
  'pwsh',
  'process.argv.slice(2)',
  'stdio: ''inherit'''
)
foreach ($snippet in $runnerSnippets) {
  Assert ($runner.Contains($snippet)) "qa/real-backend-e2e.mjs is missing required snippet: $snippet"
}

$spec = Get-Content -Path $specPath -Raw
$specSnippets = @(
  'runs login, guild/channel/message, voice, and stage through the real backend',
  'REAL_BACKEND_E2E',
  'REAL_BACKEND_BASE_URL',
  'login-success',
  'real-backend-smoke',
  'guild-name',
  'chat-viewport',
  'voice-token-provider',
  'stage-topic'
)
foreach ($snippet in $specSnippets) {
  Assert ($spec.Contains($snippet)) "real-backend.spec.ts is missing required snippet: $snippet"
}

$workflow = Get-Content -Path $workflowPath -Raw
$workflowSnippets = @(
  'qa-runtime:',
  'npx playwright install --with-deps chromium',
  'pwsh qa/real-backend-browser-smoke-default.contract.ps1',
  'pwsh qa/real-backend-e2e.contract.ps1',
  'pwsh qa/real-backend-e2e.ps1 -BackendUrl http://127.0.0.1:8080 -SkipServiceStart',
  'qa/artifacts/real-backend-e2e'
)
foreach ($snippet in $workflowSnippets) {
  Assert ($workflow.Contains($snippet)) "CI workflow is missing real backend browser gate snippet: $snippet"
}

Write-Output 'REAL_BACKEND_BROWSER_SMOKE_DEFAULT_CONTRACT_PASS'
