param(
  [string] $Container = 'postgres-source',
  [string] $Database = 'discord',
  [string] $DbUser = 'dev_user',
  [string] $DbPassword = 'dev_password',
  [int] $Port = 15432,
  [string] $SmokeUserId = ''
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $repoRoot 'infra/docker/docker-compose.yml'
$profilePath = Join-Path $repoRoot 'backend/boot/src/main/resources/application-admin-cli.yml'
$artifactDir = $env:ADMIN_CLI_ARTIFACT_DIR
if ([string]::IsNullOrWhiteSpace($artifactDir)) {
  $artifactDir = 'qa/artifacts/admin-cli/local'
}
if (-not [System.IO.Path]::IsPathRooted($artifactDir)) {
  $artifactDir = Join-Path $repoRoot $artifactDir
}
$warmupLogPath = Join-Path $artifactDir 'bootrun-migrate.log'
$listLogPath = Join-Path $artifactDir 'bootrun-list.log'
$grantLogPath = Join-Path $artifactDir 'bootrun-grant.log'
$listAfterGrantLogPath = Join-Path $artifactDir 'bootrun-list-after-grant.log'
$revokeLogPath = Join-Path $artifactDir 'bootrun-revoke.log'
$listAfterRevokeLogPath = Join-Path $artifactDir 'bootrun-list-after-revoke.log'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

function Get-GradleCommand {
  $runsOnWindows = $PSVersionTable.Platform -eq 'Win32NT' -or $env:OS -eq 'Windows_NT'
  if ($runsOnWindows) {
    return Join-Path $repoRoot 'gradlew.bat'
  }
  return Join-Path $repoRoot 'gradlew'
}

function Invoke-Native($file, [string[]] $arguments) {
  & $file @arguments
  if ($LASTEXITCODE -ne 0) {
    throw "$file failed with exit code $LASTEXITCODE"
  }
}

function New-SmokeUserId {
  return [guid]::NewGuid().ToString()
}

function Get-SmokeSuffix([string] $userId) {
  $suffix = ($userId -replace '[^A-Za-z0-9]', '').ToLowerInvariant()
  if ($suffix.Length -gt 20) {
    return $suffix.Substring(0, 20)
  }
  return $suffix
}

function Invoke-PostgresSql([string] $sql) {
  $sql | docker exec -i $Container psql -U $DbUser -d $Database -v ON_ERROR_STOP=1 | Out-Null
  if ($LASTEXITCODE -ne 0) {
    throw "psql failed with exit code $LASTEXITCODE"
  }
}

function Get-PostgresScalar([string] $sql) {
  $result = docker exec $Container psql -U $DbUser -d $Database -tAc $sql
  if ($LASTEXITCODE -ne 0) {
    throw "psql scalar query failed with exit code $LASTEXITCODE"
  }
  return ($result | Out-String).Trim()
}

function Assert-GlobalRoleState([string] $userId, [string] $role, [bool] $shouldExist) {
  $count = [int] (Get-PostgresScalar "SELECT COUNT(*) FROM user_global_roles WHERE user_id = '$userId' AND role = '$role'")
  if ($shouldExist) {
    Assert ($count -eq 1) "expected $role to be present for smoke user"
  } else {
    Assert ($count -eq 0) "expected $role to be absent for smoke user"
  }
}

function Assert-GlobalRoleAudit([string] $userId, [string] $action, [string] $result) {
  $count = [int] (Get-PostgresScalar "SELECT COUNT(*) FROM user_global_role_audit_log WHERE target_user_id = '$userId' AND role = 'SECURITY_ADMIN' AND action = '$action' AND result = '$result' AND actor = 'admin-cli-bootrun-smoke'")
  Assert ($count -ge 1) "expected audit row for $action/$result on smoke user"
}

function Remove-SmokeUser([string] $userId, [string] $email) {
  $cleanupSql = @"
DELETE FROM user_global_role_audit_log WHERE target_user_id = '$userId';
DELETE FROM user_global_roles WHERE user_id = '$userId';
DELETE FROM auth_refresh_sessions WHERE user_id = '$userId';
DELETE FROM auth_sessions WHERE user_id = '$userId';
DELETE FROM auth_accounts WHERE user_id = '$userId' OR email = '$email';
DELETE FROM users WHERE id = '$userId';
"@

  Invoke-PostgresSql $cleanupSql
}

function Test-PostgresReady {
  try {
    $containerId = docker ps --filter "name=^/$Container$" --format '{{.ID}}'
    if ([string]::IsNullOrWhiteSpace($containerId)) {
      return $false
    }
    $ready = docker exec $Container pg_isready -U $DbUser -d $Database
    return $ready -match 'accepting connections'
  } catch {
    return $false
  }
}

function Wait-PostgresReady {
  for ($attempt = 0; $attempt -lt 60; $attempt += 1) {
    if (Test-PostgresReady) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

function Ensure-PostgresContainer {
  if (Test-PostgresReady) {
    return
  }

  Invoke-Native 'docker' @(
    'compose',
    '-f',
    $compose,
    'up',
    '-d',
    $Container
  )
  Assert (Wait-PostgresReady) "Docker Postgres container did not become ready: $Container"
}

function Invoke-BootRun($bootArgs, $logPath) {
  $gradle = Get-GradleCommand
  $gradleArgs = @(
    ':backend:boot:bootRun',
    "--args=$bootArgs"
  )

  Push-Location $repoRoot
  try {
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
      $previousNativePreference = $global:PSNativeCommandUseErrorActionPreference
      $global:PSNativeCommandUseErrorActionPreference = $false
    } else {
      $previousNativePreference = $null
    }

    $output = & $gradle @gradleArgs 2>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
    if ($null -ne $previousNativePreference) {
      $global:PSNativeCommandUseErrorActionPreference = $previousNativePreference
    }
    Pop-Location
  }

  $text = ($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
  $text | Set-Content -Path $logPath -Encoding UTF8
  return @{
    ExitCode = $exitCode
    Output = $text
  }
}

function Assert-BootRunOutput($result, [string] $expectedText, [string] $logPath, [string] $description) {
  Assert ($result.ExitCode -eq 0) "admin-cli bootRun failed for ${description} with exit code $($result.ExitCode). Log: $logPath"
  Assert ($result.Output.Contains($expectedText)) "admin-cli bootRun output did not include expected ${description} text. Log: $logPath"
}

function Get-RequiredTableCount {
  $result = docker exec $Container psql -U $DbUser -d $Database -tAc "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('users', 'auth_accounts', 'user_global_roles')"
  return [int] $result.Trim()
}

New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$gradle = Get-GradleCommand
Assert (Test-Path $gradle) "Gradle wrapper not found: $gradle"
Assert (Test-Path $profilePath) "admin-cli profile not found: $profilePath"
Assert (Test-Path $compose) "Docker Compose file not found: $compose"
Assert ((Get-Content -Path $profilePath -Raw).Contains('web-application-type: none')) 'admin-cli profile must keep web-application-type: none'

Ensure-PostgresContainer

$databaseExistsOutput = docker exec $Container psql -U $DbUser -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$Database'"
$databaseExists = ($databaseExistsOutput | Out-String).Trim()
if ($databaseExists -ne '1') {
  docker exec $Container createdb -U $DbUser $Database | Out-Null
}

$env:POSTGRES_JDBC_URL = "jdbc:postgresql://127.0.0.1:$Port/$Database"
$env:POSTGRES_USER = $DbUser
$env:POSTGRES_PASSWORD = $DbPassword

$ownsSmokeUser = [string]::IsNullOrWhiteSpace($SmokeUserId)
if ($ownsSmokeUser) {
  $SmokeUserId = New-SmokeUserId
}
$smokeSuffix = Get-SmokeSuffix $SmokeUserId
$smokeUsername = "admincli$smokeSuffix"
$smokeEmail = "admin-cli-bootrun-smoke-$smokeSuffix@example.com"

$warmupArgs = "--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=list --discord.admin-role.user-id=$SmokeUserId --discord.admin-role.role=security_admin"
if ((Get-RequiredTableCount) -ne 3) {
  Invoke-BootRun $warmupArgs $warmupLogPath | Out-Null
}
Assert ((Get-RequiredTableCount) -eq 3) 'admin-cli smoke requires migrated users, auth_accounts, and user_global_roles tables'

$seedSql = @"
INSERT INTO users(id, username, display_name, created_at, updated_at)
VALUES ('$SmokeUserId', '$smokeUsername', 'Admin CLI Smoke User', now(), now())
ON CONFLICT (id) DO UPDATE
SET username = EXCLUDED.username,
    display_name = EXCLUDED.display_name,
    updated_at = now();

INSERT INTO auth_accounts(email, user_id, password_hash)
VALUES ('$smokeEmail', '$SmokeUserId', 'admin-cli-smoke-password-hash')
ON CONFLICT (email) DO UPDATE
SET user_id = EXCLUDED.user_id,
    password_hash = EXCLUDED.password_hash;
"@

try {
  Invoke-PostgresSql $seedSql

  $listArgs = "--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=list --discord.admin-role.user-id=$SmokeUserId --discord.admin-role.role=security_admin"
  $result = Invoke-BootRun $listArgs $listLogPath
  Assert-BootRunOutput $result 'global roles for' $listLogPath 'initial list'
  Assert ($result.Output.Contains($SmokeUserId)) "admin-cli bootRun output did not include smoke user id. Log: $listLogPath"
  Assert-GlobalRoleState $SmokeUserId 'SECURITY_ADMIN' $false

  $grantArgs = "--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=grant --discord.admin-role.user-id=$SmokeUserId --discord.admin-role.role=security_admin --discord.admin-role.actor=admin-cli-bootrun-smoke --discord.admin-role.confirm=true"
  $grantResult = Invoke-BootRun $grantArgs $grantLogPath
  Assert-BootRunOutput $grantResult 'granted SECURITY_ADMIN' $grantLogPath 'grant'
  Assert-GlobalRoleState $SmokeUserId 'SECURITY_ADMIN' $true
  Assert-GlobalRoleAudit $SmokeUserId 'GRANT' 'APPLIED'

  $listAfterGrantResult = Invoke-BootRun $listArgs $listAfterGrantLogPath
  Assert-BootRunOutput $listAfterGrantResult "global roles for ${SmokeUserId}: SECURITY_ADMIN" $listAfterGrantLogPath 'list after grant'

  $revokeArgs = "--spring.profiles.active=admin-cli,postgres --spring.main.web-application-type=none --discord.admin-role.command=revoke --discord.admin-role.user-id=$SmokeUserId --discord.admin-role.role=security_admin --discord.admin-role.actor=admin-cli-bootrun-smoke --discord.admin-role.confirm=true"
  $revokeResult = Invoke-BootRun $revokeArgs $revokeLogPath
  Assert-BootRunOutput $revokeResult 'revoked SECURITY_ADMIN' $revokeLogPath 'revoke'
  Assert-GlobalRoleState $SmokeUserId 'SECURITY_ADMIN' $false
  Assert-GlobalRoleAudit $SmokeUserId 'REVOKE' 'APPLIED'

  $listAfterRevokeResult = Invoke-BootRun $listArgs $listAfterRevokeLogPath
  Assert-BootRunOutput $listAfterRevokeResult "global roles for ${SmokeUserId}: " $listAfterRevokeLogPath 'list after revoke'

  Write-Output 'ADMIN_CLI_BOOTRUN_SMOKE_PASS'
} finally {
  if ($ownsSmokeUser) {
    Remove-SmokeUser $SmokeUserId $smokeEmail
  }
}
