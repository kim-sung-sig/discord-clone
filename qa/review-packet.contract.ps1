$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'new-review-packet.ps1'
$harnessPath = Join-Path $PSScriptRoot 'agent-harness.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $scriptPath) 'qa/new-review-packet.ps1 is missing'

$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref] $tokens, [ref] $parseErrors) | Out-Null
Assert ($parseErrors.Count -eq 0) "qa/new-review-packet.ps1 has parse errors: $($parseErrors | ConvertTo-Json -Compress)"

$content = Get-Content -Path $scriptPath -Raw
foreach ($snippet in @(
  '[string] $TaskId',
  '[string[]] $Paths',
  '[string[]] $PlanDesignPaths',
  'Diff-Only Review Packet',
  'git diff --stat',
  'git diff --',
  'Forbidden Context',
  'implementer scratch notes',
  'P0/P1/P2',
  'review-packets'
)) {
  Assert ($content.Contains($snippet)) "qa/new-review-packet.ps1 is missing required snippet: $snippet"
}

$harness = Get-Content -Path $harnessPath -Raw
Assert ($harness.Contains("'review-packet-contract'")) 'qa/agent-harness.ps1 is missing review-packet-contract tool id'

Write-Output 'REVIEW_PACKET_CONTRACT_PASS'
