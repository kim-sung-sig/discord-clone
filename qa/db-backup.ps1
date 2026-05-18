param(
  [Parameter(Mandatory = $true)]
  [string] $SourceJdbcUrl,
  [string] $PostgresUser = 'dev_user',
  [string] $PostgresPassword = 'dev_password',
  [string] $ArtifactsDir = 'qa/artifacts/db-drill',
  [string] $PostgresCliContainer = '',
  [switch] $AllowNonLocal
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'db-drill-common.ps1')

$repoRoot = Resolve-RepoRoot
$artifactRoot = if ([System.IO.Path]::IsPathRooted($ArtifactsDir)) { $ArtifactsDir } else { Join-Path $repoRoot $ArtifactsDir }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $artifactRoot $stamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$parsedSource = Assert-SafeJdbcUrl $SourceJdbcUrl -AllowNonLocal:$AllowNonLocal

$dumpPath = Join-Path $runDir 'source.dump'
$containerDumpPath = "/tmp/discord-clone-$stamp-source.dump"
$backupLog = Join-Path $runDir 'backup.log'
$metadataPath = Join-Path $runDir 'backup-metadata.txt'

@(
  "timestamp=$stamp"
  "source_jdbc_url=$($parsedSource.Redacted)"
  'source_password=redacted'
  'artifact_type=pg_dump_custom'
) | Set-Content -Path $metadataPath

if (-not [string]::IsNullOrWhiteSpace($PostgresCliContainer)) {
  Assert-CommandAvailable 'docker' 'Docker is required when -PostgresCliContainer is used.'
  Invoke-LoggedCommand `
    'pg_dump source database in docker container' `
    'docker' `
    @('exec', '-e', "PGPASSWORD=$PostgresPassword", $PostgresCliContainer, 'pg_dump', '-h', '127.0.0.1', '-p', '5432', '-U', $PostgresUser, '-d', $parsedSource.Database, '-Fc', '--no-owner', '--file', $containerDumpPath) `
    $repoRoot `
    $backupLog
  Invoke-LoggedCommand `
    'copy source dump from docker container' `
    'docker' `
    @('cp', "$PostgresCliContainer`:$containerDumpPath", $dumpPath) `
    $repoRoot `
    $backupLog
} else {
  Assert-CommandAvailable 'pg_dump' 'Windows users can install PostgreSQL or add the PostgreSQL bin directory to PATH.'
  Invoke-LoggedCommand `
    'pg_dump source database' `
    'pg_dump' `
    @('-h', $parsedSource.Host, '-p', $parsedSource.Port, '-U', $PostgresUser, '-d', $parsedSource.Database, '-Fc', '--no-owner', '--file', $dumpPath) `
    $repoRoot `
    $backupLog `
    @{ PGPASSWORD = $PostgresPassword }
}

Write-Output "DB_BACKUP_PASS dump=$dumpPath metadata=$metadataPath"
