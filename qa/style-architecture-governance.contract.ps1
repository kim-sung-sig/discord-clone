$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$harnessPath = Join-Path $PSScriptRoot 'agent-harness.ps1'
$backendContractPath = Join-Path $PSScriptRoot 'backend-style-contract.ps1'
$frontendContractPath = Join-Path $PSScriptRoot 'frontend-style-contract.ps1'
$processContractPath = Join-Path $PSScriptRoot 'development-process-contract.ps1'
$guidePath = Join-Path $repoRoot 'docs/03-tasking/style-architecture-governance.md'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

function Assert-ParseablePowerShell($path) {
  $tokens = $null
  $parseErrors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($path, [ref] $tokens, [ref] $parseErrors) | Out-Null
  Assert ($parseErrors.Count -eq 0) "$path has parse errors: $($parseErrors | ConvertTo-Json -Compress)"
}

Assert (Test-Path $harnessPath) 'qa/agent-harness.ps1 is missing'
Assert (Test-Path $backendContractPath) 'qa/backend-style-contract.ps1 is missing'
Assert (Test-Path $frontendContractPath) 'qa/frontend-style-contract.ps1 is missing'
Assert (Test-Path $processContractPath) 'qa/development-process-contract.ps1 is missing'
Assert (Test-Path $guidePath) 'docs/03-tasking/style-architecture-governance.md is missing'

foreach ($script in @($backendContractPath, $frontendContractPath, $processContractPath)) {
  Assert-ParseablePowerShell $script
}

$harness = Get-Content -Path $harnessPath -Raw
foreach ($toolId in @('backend-style-contract', 'frontend-style-contract', 'development-process-contract', 'style-architecture-governance-contract')) {
  Assert ($harness.Contains("'$toolId'")) "qa/agent-harness.ps1 is missing tool id: $toolId"
}

$guide = Get-Content -Path $guidePath -Raw
foreach ($snippet in @(
  '```mermaid',
  'classDiagram',
  'flowchart',
  'TDD Evidence',
  'Ubiquitous Language',
  'DTO Boundary',
  'Method Signature',
  'Harness Gates'
)) {
  Assert ($guide.Contains($snippet)) "style governance guide is missing required content: $snippet"
}

Write-Output 'STYLE_ARCHITECTURE_GOVERNANCE_CONTRACT_PASS'
