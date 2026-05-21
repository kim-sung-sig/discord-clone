$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'agent-harness.ps1'
$statePath = Join-Path $repoRoot 'docs/03-tasking/agent-loop-state.md'
$modelPath = Join-Path $repoRoot 'docs/03-tasking/agent-harness-operating-model.md'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $scriptPath) 'qa/agent-harness.ps1 is missing'
Assert (Test-Path $statePath) 'docs/03-tasking/agent-loop-state.md is missing'
Assert (Test-Path $modelPath) 'docs/03-tasking/agent-harness-operating-model.md is missing'

$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref] $tokens, [ref] $parseErrors) | Out-Null
Assert ($parseErrors.Count -eq 0) "qa/agent-harness.ps1 has parse errors: $($parseErrors | ConvertTo-Json -Compress)"

$content = Get-Content -Path $scriptPath -Raw
$requiredSnippets = @(
  '[string] $Tool',
  '[switch] $List',
  '$ArtifactsDir = ''qa/artifacts/agent-harness''',
  'backend-test',
  'backend-boot-test',
  'web-test',
  'web-build',
  'web-e2e',
  'openapi-check',
  'docker-config',
  'api-smoke',
  'real-backend-e2e-contract',
  'real-backend-e2e',
  'ci-workflow-contract',
  'toolchain-warning-scan',
  'migration-guard-contract',
  'backend-style-contract',
  'frontend-style-contract',
  'development-process-contract',
  'style-architecture-governance-contract',
  'review-context-isolation-contract',
  'task-complete-contract',
  'review-packet-contract',
  'real-lint-contract',
  'process-tree-cleanup-contract',
  'playwright-port-isolation-contract',
  'subagent-role-packets-contract',
  'frontend-lint',
  'backend-lint',
  'format-check',
  'Unknown agent harness tool',
  'Start-Transcript',
  'agent-harness-state.json',
  'lastResult',
  'PASS',
  'FAIL'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "qa/agent-harness.ps1 is missing required snippet: $snippet"
}

$stateContent = Get-Content -Path $statePath -Raw
foreach ($snippet in @('activeTask', 'phase', 'lastTool', 'lastResult', 'nextAction', 'blocked')) {
  Assert ($stateContent.Contains($snippet)) "agent-loop-state.md is missing required field: $snippet"
}

$modelContent = Get-Content -Path $modelPath -Raw
foreach ($snippet in @('Tool ID Allowlist', 'PDCA Loop', 'Ticket Creation', 'Loop State')) {
  Assert ($modelContent.Contains($snippet)) "agent-harness-operating-model.md is missing section: $snippet"
}

Write-Output 'AGENT_HARNESS_CONTRACT_PASS'
