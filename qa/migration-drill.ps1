param(
  [Parameter(Mandatory = $true)]
  [string] $SourceJdbcUrl,
  [Parameter(Mandatory = $true)]
  [string] $TargetJdbcUrl,
  [string] $PostgresUser = 'dev_user',
  [string] $PostgresPassword = 'dev_password',
  [string] $PostgresCliContainer = '',
  [string] $BackendUrl = 'http://127.0.0.1:8080',
  [string] $ArtifactsDir = 'qa/artifacts/db-drill',
  [int] $BackendStartupTimeoutSeconds = 120,
  [switch] $AllowNonLocal,
  [switch] $SkipServiceStart
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'db-drill-common.ps1')

$repoRoot = Resolve-RepoRoot
if ($SourceJdbcUrl -eq $TargetJdbcUrl) {
  throw 'SourceJdbcUrl -eq TargetJdbcUrl; refusing to run destructive restore drill'
}

$artifactRoot = if ([System.IO.Path]::IsPathRooted($ArtifactsDir)) { $ArtifactsDir } else { Join-Path $repoRoot $ArtifactsDir }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $artifactRoot $stamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$source = Assert-SafeJdbcUrl $SourceJdbcUrl -AllowNonLocal:$AllowNonLocal
$target = Assert-SafeJdbcUrl $TargetJdbcUrl -AllowNonLocal:$AllowNonLocal
Assert-SourceAndTargetDiffer $SourceJdbcUrl $TargetJdbcUrl

$backendBaseUrl = $BackendUrl.TrimEnd('/')
$healthUrl = "$backendBaseUrl/actuator/health"
$backendPort = ([uri] $backendBaseUrl).Port
$backendLog = Join-Path $runDir 'backend-bootRun.log'
$backendErrorLog = Join-Path $runDir 'backend-bootRun.err.log'
$sourceSeedLog = Join-Path $runDir 'source-seed-api-smoke.log'
$apiSmokeLog = Join-Path $runDir 'api-smoke.log'
$guardLog = Join-Path $runDir 'migration-guard.log'
$backupLog = Join-Path $runDir 'db-backup.log'
$restoreLog = Join-Path $runDir 'db-restore.log'
$sourceSnapshotHashPath = Join-Path $runDir 'source-snapshot-hashes.tsv'
$restoredSnapshotHashPath = Join-Path $runDir 'restored-snapshot-hashes.tsv'
$snapshotHashDiffPath = Join-Path $runDir 'snapshot-hash-comparison.txt'
$summaryPath = Join-Path $runDir 'restore-drill-summary.md'
$backendProcess = $null

function Test-BackendHealth {
  try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -Method GET -TimeoutSec 3
    return [int] $response.StatusCode -ge 200 -and [int] $response.StatusCode -lt 500
  } catch {
    return $false
  }
}

function Start-BackendService([string] $JdbcUrl, [string] $OutputLog, [string] $ErrorLog) {
  $gradlePath = Get-GradleWrapper
  if (-not (Test-Path $gradlePath)) {
    throw "Gradle wrapper not found: $gradlePath"
  }

  $previous = Set-TemporaryEnvironment @{
    SPRING_PROFILES_ACTIVE = 'postgres'
    POSTGRES_JDBC_URL = $JdbcUrl
    POSTGRES_USER = $PostgresUser
    POSTGRES_PASSWORD = $PostgresPassword
    SERVER_PORT = $backendPort
    MANAGEMENT_HEALTH_REDIS_ENABLED = 'false'
  }
  try {
    $isWindows = Test-IsWindows
    $startParams = @{
      FilePath = if ($isWindows) { 'cmd.exe' } else { 'bash' }
      ArgumentList = if ($isWindows) { @('/c', $gradlePath, ':backend:boot:bootRun') } else { @('-lc', './gradlew :backend:boot:bootRun') }
      WorkingDirectory = $repoRoot
      RedirectStandardOutput = $OutputLog
      RedirectStandardError = $ErrorLog
      PassThru = $true
    }
    if ($isWindows) {
      $startParams.WindowStyle = 'Hidden'
    }
    return Start-Process @startParams
  } finally {
    Restore-TemporaryEnvironment $previous
  }
}

function Wait-BackendHealth {
  $deadline = (Get-Date).AddSeconds($BackendStartupTimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    if (Test-BackendHealth) {
      return
    }
    if ($null -ne $backendProcess -and $backendProcess.HasExited) {
      throw "Backend process exited before health became ready. See $backendLog and $backendErrorLog"
    }
    Start-Sleep -Seconds 2
  }
  throw "Backend health did not become ready within $BackendStartupTimeoutSeconds seconds at $healthUrl"
}

function Stop-BackendService {
  if ($null -ne $backendProcess -and -not $backendProcess.HasExited) {
    Write-DrillStep "stopping backend process $($backendProcess.Id)"
    Stop-Process -Id $backendProcess.Id -Force
  }
  $script:backendProcess = $null
}

try {
  Write-DrillStep "artifacts=$runDir"
  Invoke-LoggedCommand `
    'destructive migration guard' `
    (Get-PowerShellCommand) `
    @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repoRoot 'qa/migration-guard.contract.ps1')) `
    $repoRoot `
    $guardLog

  if (Test-BackendHealth) {
    Write-DrillStep "reusing existing backend for source seed: $healthUrl"
  } elseif ($SkipServiceStart) {
    throw "Backend is not healthy at $healthUrl and -SkipServiceStart was set"
  } else {
    $backendProcess = Start-BackendService $SourceJdbcUrl $backendLog $backendErrorLog
    Wait-BackendHealth
  }

  Invoke-LoggedCommand `
    'seed source database through API smoke' `
    (Get-PowerShellCommand) `
    @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repoRoot 'qa/api-smoke.ps1'), '-BaseUrl', $backendBaseUrl) `
    $repoRoot `
    $sourceSeedLog

  if (-not $SkipServiceStart) {
    Stop-BackendService
    Start-Sleep -Seconds 2
  }

  Write-DatabaseSnapshotHash $SourceJdbcUrl $PostgresUser $PostgresPassword $PostgresCliContainer $sourceSnapshotHashPath

  Invoke-LoggedCommand `
    'source database backup' `
    (Get-PowerShellCommand) `
    @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repoRoot 'qa/db-backup.ps1'), '-SourceJdbcUrl', $SourceJdbcUrl, '-PostgresUser', $PostgresUser, '-PostgresPassword', $PostgresPassword, '-ArtifactsDir', $runDir, '-PostgresCliContainer', $PostgresCliContainer) `
    $repoRoot `
    $backupLog

  $dumpPath = Get-ChildItem -Path $runDir -Filter 'source.dump' -Recurse | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if ($null -eq $dumpPath) {
    throw "Backup did not create source.dump under $runDir"
  }

  Invoke-LoggedCommand `
    'target database restore' `
    (Get-PowerShellCommand) `
    @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repoRoot 'qa/db-restore.ps1'), '-DumpPath', $dumpPath.FullName, '-SourceJdbcUrl', $SourceJdbcUrl, '-TargetJdbcUrl', $TargetJdbcUrl, '-PostgresUser', $PostgresUser, '-PostgresPassword', $PostgresPassword, '-ArtifactsDir', $runDir, '-PostgresCliContainer', $PostgresCliContainer, '-ConfirmCleanTarget', '-EnsureTargetDatabase') `
    $repoRoot `
    $restoreLog

  Write-DatabaseSnapshotHash $TargetJdbcUrl $PostgresUser $PostgresPassword $PostgresCliContainer $restoredSnapshotHashPath
  Compare-DatabaseSnapshotHashes $sourceSnapshotHashPath $restoredSnapshotHashPath $snapshotHashDiffPath

  if (Test-BackendHealth) {
    Write-DrillStep "reusing existing backend: $healthUrl"
  } elseif ($SkipServiceStart) {
    throw "Backend is not healthy at $healthUrl and -SkipServiceStart was set"
  } else {
    $backendProcess = Start-BackendService $TargetJdbcUrl $backendLog $backendErrorLog
    Wait-BackendHealth
  }

  Invoke-LoggedCommand `
    'API smoke after restore' `
    (Get-PowerShellCommand) `
    @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repoRoot 'qa/api-smoke.ps1'), '-BaseUrl', $backendBaseUrl) `
    $repoRoot `
    $apiSmokeLog

  @(
    '# T39 Restore Drill Summary'
    ''
    "timestamp=$stamp"
    "source_jdbc_url=$($source.Redacted)"
    "target_jdbc_url=$($target.Redacted)"
    'source_password=redacted'
    'target_password=redacted'
    'retention_keep_latest=5'
    "dump_file=$($dumpPath.FullName)"
    "source_seed_log=$sourceSeedLog"
    "source_snapshot_hashes=$sourceSnapshotHashPath"
    "restored_snapshot_hashes=$restoredSnapshotHashPath"
    "snapshot_hash_comparison=PASS"
    "restore_api_smoke_log=$apiSmokeLog"
    'result=PASS'
    ''
    'Retention policy: keep latest 5 local drill directories; CI artifacts rely on CI retention; never commit dump files.'
  ) | Set-Content -Path $summaryPath

  Remove-OldDrillArtifacts $artifactRoot 5
  Write-Output "RESTORE_DRILL_PASS artifacts=$runDir summary=$summaryPath"
} finally {
  Stop-BackendService
}
