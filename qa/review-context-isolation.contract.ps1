$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$agentModelPath = Join-Path $repoRoot 'docs/03-tasking/agent-team-operating-model.md'
$harnessModelPath = Join-Path $repoRoot 'docs/03-tasking/agent-harness-operating-model.md'
$harnessPath = Join-Path $PSScriptRoot 'agent-harness.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $agentModelPath) 'docs/03-tasking/agent-team-operating-model.md is missing'
Assert (Test-Path $harnessModelPath) 'docs/03-tasking/agent-harness-operating-model.md is missing'
Assert (Test-Path $harnessPath) 'qa/agent-harness.ps1 is missing'

$agentModel = Get-Content -Path $agentModelPath -Raw
$harnessModel = Get-Content -Path $harnessModelPath -Raw
$harness = Get-Content -Path $harnessPath -Raw

foreach ($snippet in @(
  'Review Context Isolation',
  'Diff-Only Review Packet',
  'review agents start from empty task context',
  'reviewer must not receive implementer scratch notes',
  'git diff --stat',
  'git diff --',
  'P0/P1/P2'
)) {
  Assert ($agentModel.Contains($snippet)) "agent-team-operating-model.md is missing review isolation rule: $snippet"
}

foreach ($snippet in @(
  'Commit And Push Gate',
  'commit only the task-owned paths',
  'push after the commit when remote is configured',
  'do not commit unrelated dirty work'
)) {
  Assert ($agentModel.Contains($snippet) -or $harnessModel.Contains($snippet)) "task completion commit/push rule is missing: $snippet"
}

Assert ($harness.Contains("'review-context-isolation-contract'")) 'qa/agent-harness.ps1 is missing review-context-isolation-contract tool id'

Write-Output 'REVIEW_CONTEXT_ISOLATION_CONTRACT_PASS'
