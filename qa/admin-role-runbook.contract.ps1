$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$runbookPath = Join-Path $repoRoot 'docs/runbooks/global-admin-role-runbook.md'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $runbookPath) 'docs/runbooks/global-admin-role-runbook.md is missing'

$content = Get-Content -Path $runbookPath -Raw
$requiredSnippets = @(
  'SECURITY_ADMIN',
  'POSTGRES_JDBC_URL',
  'POSTGRES_USER',
  'POSTGRES_PASSWORD',
  'spring.profiles.active=admin-cli,postgres',
  'discord.admin-role.command=grant',
  'discord.admin-role.command=list',
  'discord.admin-role.command=revoke',
  'discord.admin-role.confirm=true',
  'discord.admin-role.actor',
  '/api/users/@me',
  '/api/admin/global-roles/audit-log',
  'qa/admin-cli-bootrun-smoke.ps1',
  'Do not pass database passwords in Gradle --args',
  'Rollback',
  'Pre-check'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "admin role runbook is missing required snippet: $snippet"
}

Write-Output 'ADMIN_ROLE_RUNBOOK_CONTRACT_PASS'
