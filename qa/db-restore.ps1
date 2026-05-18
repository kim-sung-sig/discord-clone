param(
  [Parameter(Mandatory = $true)]
  [string] $DumpPath,
  [Parameter(Mandatory = $true)]
  [string] $TargetJdbcUrl,
  [string] $SourceJdbcUrl = '',
  [string] $PostgresUser = 'dev_user',
  [string] $PostgresPassword = 'dev_password',
  [string] $ArtifactsDir = 'qa/artifacts/db-drill',
  [string] $PostgresCliContainer = '',
  [switch] $ConfirmCleanTarget,
  [switch] $AllowNonLocal
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'db-drill-common.ps1')

if (-not (Test-Path $DumpPath)) {
  throw "Dump file not found: $DumpPath"
}
if (-not $ConfirmCleanTarget) {
  throw 'Restore requires -ConfirmCleanTarget because it drops and recreates the public schema.'
}

$repoRoot = Resolve-RepoRoot
$artifactRoot = if ([System.IO.Path]::IsPathRooted($ArtifactsDir)) { $ArtifactsDir } else { Join-Path $repoRoot $ArtifactsDir }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $artifactRoot $stamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

if (-not [string]::IsNullOrWhiteSpace($SourceJdbcUrl) -and $SourceJdbcUrl.Trim() -eq $TargetJdbcUrl.Trim()) {
  throw 'refusing to restore into source database: source and target JDBC URLs are identical'
}
Assert-SourceAndTargetDiffer $SourceJdbcUrl $TargetJdbcUrl
$parsedTarget = Assert-SafeJdbcUrl $TargetJdbcUrl -AllowNonLocal:$AllowNonLocal

$restoreLog = Join-Path $runDir 'restore.log'
$metadataPath = Join-Path $runDir 'restore-metadata.txt'
$containerDumpPath = "/tmp/discord-clone-$stamp-restore.dump"

@(
  "timestamp=$stamp"
  "target_jdbc_url=$($parsedTarget.Redacted)"
  'target_password=redacted'
  "dump_path=$DumpPath"
) | Set-Content -Path $metadataPath

if (-not [string]::IsNullOrWhiteSpace($PostgresCliContainer)) {
  Assert-CommandAvailable 'docker' 'Docker is required when -PostgresCliContainer is used.'
  Invoke-LoggedCommand `
    'copy dump into docker container' `
    'docker' `
    @('cp', $DumpPath, "$PostgresCliContainer`:$containerDumpPath") `
    $repoRoot `
    $restoreLog
  Invoke-LoggedCommand `
    'clean restore target schema in docker container' `
    'docker' `
    @('exec', '-e', "PGPASSWORD=$PostgresPassword", $PostgresCliContainer, 'psql', '-h', '127.0.0.1', '-p', '5432', '-U', $PostgresUser, '-d', $parsedTarget.Database, '-v', 'ON_ERROR_STOP=1', '-c', 'DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;') `
    $repoRoot `
    $restoreLog
  Invoke-LoggedCommand `
    'pg_restore target database in docker container' `
    'docker' `
    @('exec', '-e', "PGPASSWORD=$PostgresPassword", $PostgresCliContainer, 'pg_restore', '-h', '127.0.0.1', '-p', '5432', '-U', $PostgresUser, '-d', $parsedTarget.Database, '--no-owner', '--if-exists', '--clean', $containerDumpPath) `
    $repoRoot `
    $restoreLog
} else {
  Assert-CommandAvailable 'psql' 'Windows users can install PostgreSQL or add the PostgreSQL bin directory to PATH.'
  Assert-CommandAvailable 'pg_restore' 'Windows users can install PostgreSQL or add the PostgreSQL bin directory to PATH.'
  Invoke-LoggedCommand `
    'clean restore target schema' `
    'psql' `
    @('-h', $parsedTarget.Host, '-p', $parsedTarget.Port, '-U', $PostgresUser, '-d', $parsedTarget.Database, '-v', 'ON_ERROR_STOP=1', '-c', 'DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;') `
    $repoRoot `
    $restoreLog `
    @{ PGPASSWORD = $PostgresPassword }
  Invoke-LoggedCommand `
    'pg_restore target database' `
    'pg_restore' `
    @('-h', $parsedTarget.Host, '-p', $parsedTarget.Port, '-U', $PostgresUser, '-d', $parsedTarget.Database, '--no-owner', '--if-exists', '--clean', $DumpPath) `
    $repoRoot `
    $restoreLog `
    @{ PGPASSWORD = $PostgresPassword }
}

Write-Output "DB_RESTORE_PASS target=$($parsedTarget.Redacted) metadata=$metadataPath"
