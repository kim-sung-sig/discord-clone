$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'task-complete.ps1'
$harnessPath = Join-Path $PSScriptRoot 'agent-harness.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $scriptPath) 'qa/task-complete.ps1 is missing'

$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref] $tokens, [ref] $parseErrors) | Out-Null
Assert ($parseErrors.Count -eq 0) "qa/task-complete.ps1 has parse errors: $($parseErrors | ConvertTo-Json -Compress)"

$content = Get-Content -Path $scriptPath -Raw
foreach ($snippet in @(
  '[string] $TaskId',
  '[string[]] $Paths',
  '[string] $Message',
  '[switch] $NoPush',
  'git status --short',
  'git add --',
  'git diff --cached --stat',
  'git commit -m',
  'git push origin',
  'do not commit unrelated dirty work',
  'task-owned paths'
)) {
  Assert ($content.Contains($snippet)) "qa/task-complete.ps1 is missing required snippet: $snippet"
}

$harness = Get-Content -Path $harnessPath -Raw
Assert ($harness.Contains("'task-complete-contract'")) 'qa/agent-harness.ps1 is missing task-complete-contract tool id'

Write-Output 'TASK_COMPLETE_CONTRACT_PASS'
