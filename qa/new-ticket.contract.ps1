$ErrorActionPreference = 'Stop'

$scriptPath = Join-Path $PSScriptRoot 'new-ticket.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $scriptPath) 'qa/new-ticket.ps1 is missing'

$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref] $tokens, [ref] $parseErrors) | Out-Null
Assert ($parseErrors.Count -eq 0) "qa/new-ticket.ps1 has parse errors: $($parseErrors | ConvertTo-Json -Compress)"

$content = Get-Content -Path $scriptPath -Raw
$requiredSnippets = @(
  '[string] $Id',
  '[string] $Title',
  '[string] $Type',
  '[string] $Priority',
  '[switch] $Force',
  'docs/01-plan/features',
  'docs/02-design/features',
  'docs/03-analysis',
  'docs/04-report',
  'docs/05-feedback',
  'improvement-task-backlog.md',
  'PDCA Phase: Plan',
  'PDCA Phase: Design',
  'PDCA Phase: Check',
  'PDCA Phase: Report',
  'PDCA Phase: Act',
  'Allowed Write Paths',
  'Verification Commands',
  'Agent Packet',
  'NEW_TICKET_CREATED'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "qa/new-ticket.ps1 is missing required snippet: $snippet"
}

Write-Output 'NEW_TICKET_CONTRACT_PASS'
