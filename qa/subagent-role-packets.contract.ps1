$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$docPath = Join-Path $repoRoot 'docs/03-tasking/subagent-role-packets.md'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $docPath) 'docs/03-tasking/subagent-role-packets.md is missing'

$content = Get-Content -Path $docPath -Raw
$requiredSnippets = @(
  'Developer Agent',
  'QA/Spec Agent',
  'Code Quality Agent',
  'Observer/Test Agent',
  'Runtime QA Agent',
  'Role: Runtime QA Agent',
  'Diff-Only Review Packet',
  'TDD requirement',
  'Verification scope',
  'Commands to run',
  'Artifact paths',
  'Expected runtime boundaries',
  'RED evidence',
  'GREEN evidence',
  'P0/P1/P2 findings',
  'Commit is blocked',
  'Push only when local and CI-risk gates allow it',
  'npm.cmd run e2e:web:isolated'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "subagent role packet doc missing required snippet: $snippet"
}

Write-Output 'SUBAGENT_ROLE_PACKETS_CONTRACT_PASS'
