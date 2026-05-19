$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$guidePath = Join-Path $repoRoot 'docs/03-tasking/style-architecture-governance.md'
$agentModelPath = Join-Path $repoRoot 'docs/03-tasking/agent-team-operating-model.md'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $guidePath) 'docs/03-tasking/style-architecture-governance.md is missing'
Assert (Test-Path $agentModelPath) 'docs/03-tasking/agent-team-operating-model.md is missing'

$guide = Get-Content -Path $guidePath -Raw
$agentModel = Get-Content -Path $agentModelPath -Raw

foreach ($snippet in @(
  'RED',
  'GREEN',
  'REFACTOR',
  'TDD Evidence',
  'Ubiquitous Language',
  'DTO Boundary',
  'Method Signature',
  'Layer Boundary'
)) {
  Assert ($guide.Contains($snippet)) "style governance guide is missing process rule: $snippet"
}

foreach ($snippet in @(
  'TDD Evidence',
  'backend-style-contract',
  'frontend-style-contract',
  'development-process-contract'
)) {
  Assert (($guide.Contains($snippet) -or $agentModel.Contains($snippet))) "agent operating model does not reference process gate: $snippet"
}

Write-Output 'DEVELOPMENT_PROCESS_CONTRACT_PASS'
