$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $PSScriptRoot 'admin-cli-bootrun-smoke.ps1'
$profilePath = Join-Path $repoRoot 'backend/boot/src/main/resources/application-admin-cli.yml'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $scriptPath) 'qa/admin-cli-bootrun-smoke.ps1 is missing'
Assert (Test-Path $profilePath) 'application-admin-cli.yml is missing'

$tokens = $null
$parseErrors = $null
[System.Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref] $tokens, [ref] $parseErrors) | Out-Null
Assert ($parseErrors.Count -eq 0) "qa/admin-cli-bootrun-smoke.ps1 has parse errors: $($parseErrors | ConvertTo-Json -Compress)"

$profileContent = Get-Content -Path $profilePath -Raw
Assert ($profileContent.Contains('web-application-type: none')) 'admin-cli profile must keep web-application-type: none'

$content = Get-Content -Path $scriptPath -Raw
$requiredSnippets = @(
  'Get-GradleCommand',
  'New-SmokeUserId',
  'Remove-SmokeUser',
  'compose',
  '-f',
  'postgres-source',
  '--spring.main.web-application-type=none',
  'ADMIN_CLI_ARTIFACT_DIR',
  'bootrun-list.log',
  'bootrun-grant.log',
  'bootrun-list-after-grant.log',
  'bootrun-revoke.log',
  'bootrun-list-after-revoke.log',
  ':backend:boot:bootRun',
  'spring.profiles.active=admin-cli,postgres',
  'discord.admin-role.command=list',
  'discord.admin-role.command=grant',
  'discord.admin-role.command=revoke',
  'discord.admin-role.user-id',
  'discord.admin-role.confirm=true',
  'discord.admin-role.actor=admin-cli-bootrun-smoke',
  '[guid]::NewGuid()',
  'admin-cli-bootrun-smoke-',
  'finally',
  'DELETE FROM user_global_role_audit_log',
  'DELETE FROM user_global_roles',
  'DELETE FROM auth_accounts',
  'DELETE FROM users',
  'postgres-source',
  '15432',
  'dev_user',
  'dev_password',
  'global roles for',
  'granted SECURITY_ADMIN',
  'revoked SECURITY_ADMIN',
  'Assert-GlobalRoleAudit',
  'Assert-GlobalRoleState',
  'ADMIN_CLI_BOOTRUN_SMOKE_PASS'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "qa/admin-cli-bootrun-smoke.ps1 is missing required snippet: $snippet"
}

$forbiddenSnippets = @(
  "SmokeUserId = '00000000-0000-0000-0000-000000001121'",
  "admin-cli-bootrun-smoke@example.com",
  '$isWindows ='
)

foreach ($snippet in $forbiddenSnippets) {
  Assert (-not $content.Contains($snippet)) "qa/admin-cli-bootrun-smoke.ps1 still contains shared-state fixture: $snippet"
}

Write-Output 'ADMIN_CLI_BOOTRUN_CONTRACT_PASS'
