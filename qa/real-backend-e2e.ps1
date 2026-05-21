param(
  [string] $BackendUrl = 'http://127.0.0.1:18080',
  [string] $PostgresJdbcUrl = 'jdbc:postgresql://127.0.0.1:15432/discord',
  [string] $PostgresUser = 'dev_user',
  [string] $PostgresPassword = 'dev_password',
  [string] $ArtifactsDir = 'qa/artifacts/real-backend-e2e',
  [int] $BackendStartupTimeoutSeconds = 120,
  [switch] $SkipServiceStart
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendBaseUrl = $BackendUrl.TrimEnd('/')
$backendUri = [Uri] $backendBaseUrl
$backendPort = if ($backendUri.IsDefaultPort) {
  if ($backendUri.Scheme -eq 'https') { 443 } else { 80 }
} else {
  $backendUri.Port
}
$healthUrl = "$backendBaseUrl/actuator/health"
$artifactRoot = if ([System.IO.Path]::IsPathRooted($ArtifactsDir)) { $ArtifactsDir } else { Join-Path $repoRoot $ArtifactsDir }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $artifactRoot $stamp
New-Item -ItemType Directory -Force -Path $runDir | Out-Null

$backendLog = Join-Path $runDir 'backend-bootRun.log'
$backendErrorLog = Join-Path $runDir 'backend-bootRun.err.log'
$apiSmokeLog = Join-Path $runDir 'api-smoke.log'
$playwrightLog = Join-Path $runDir 'real-backend-playwright.log'
$composeHealthLog = Join-Path $runDir 'central-compose-health.log'
$metadataLog = Join-Path $runDir 'run-metadata.txt'
$backendProcess = $null
$backendStartedByScript = $false

function Test-IsWindows {
  return [System.Environment]::OSVersion.Platform -eq 'Win32NT'
}

function Get-GradleWrapper {
  if (Test-IsWindows) {
    return Join-Path $repoRoot 'gradlew.bat'
  }
  return Join-Path $repoRoot 'gradlew'
}

function Get-NpmCommand {
  if (Test-IsWindows) {
    return 'npm.cmd'
  }
  return 'npm'
}

function Get-PowerShellCommand {
  if (Test-IsWindows) {
    return 'powershell'
  }
  return 'pwsh'
}

function Write-Step($message) {
  Write-Output "[real-backend-e2e] $message"
}

function Test-BackendHealth {
  try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri $healthUrl -Method GET -TimeoutSec 3
    return [int] $response.StatusCode -ge 200 -and [int] $response.StatusCode -lt 500
  } catch {
    return $false
  }
}

function Set-TemporaryEnvironment($environment) {
  $previous = @{}
  foreach ($key in $environment.Keys) {
    $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    [Environment]::SetEnvironmentVariable($key, [string] $environment[$key], 'Process')
  }
  return $previous
}

function Restore-TemporaryEnvironment($previous) {
  foreach ($key in $previous.Keys) {
    [Environment]::SetEnvironmentVariable($key, $previous[$key], 'Process')
  }
}

function Start-BackendService {
  $gradlePath = Get-GradleWrapper
  if (-not (Test-Path $gradlePath)) {
    throw "Gradle wrapper not found: $gradlePath"
  }

  $environment = @{
    SPRING_PROFILES_ACTIVE = 'postgres'
    SERVER_PORT = [string] $backendPort
    POSTGRES_JDBC_URL = $PostgresJdbcUrl
    POSTGRES_USER = $PostgresUser
    POSTGRES_PASSWORD = $PostgresPassword
    MANAGEMENT_HEALTH_REDIS_ENABLED = 'false'
  }
  $previous = Set-TemporaryEnvironment $environment
  try {
    Write-Step "starting backend with :backend:boot:bootRun; logs=$backendLog"
    $isWindows = Test-IsWindows
    $startParams = @{
      FilePath = if ($isWindows) { 'cmd.exe' } else { 'bash' }
      ArgumentList = if ($isWindows) { @('/c', $gradlePath, ':backend:boot:bootRun') } else { @('-lc', './gradlew :backend:boot:bootRun') }
      WorkingDirectory = $repoRoot
      RedirectStandardOutput = $backendLog
      RedirectStandardError = $backendErrorLog
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
      Write-Step "backend health is ready: $healthUrl"
      return
    }

    if ($null -ne $backendProcess -and $backendProcess.HasExited) {
      throw "Backend process exited before health became ready. See $backendLog and $backendErrorLog"
    }

    Start-Sleep -Seconds 2
  }

  throw "Backend health did not become ready within $BackendStartupTimeoutSeconds seconds at $healthUrl. See $backendLog and $backendErrorLog"
}

function Stop-OwnedBackendService {
  if ($null -ne $backendProcess -and -not $backendProcess.HasExited) {
    Write-Step "stopping backend process $($backendProcess.Id)"
    Stop-Process -Id $backendProcess.Id -Force
  }

  if (-not $backendStartedByScript) {
    return
  }

  $listener = Get-NetTCPConnection -LocalPort $backendPort -ErrorAction SilentlyContinue | Select-Object -First 1
  if ($null -eq $listener) {
    return
  }

  $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)" -ErrorAction SilentlyContinue
  if ($null -ne $process -and $process.CommandLine -like '*com.example.discord.DiscordApplication*') {
    Write-Step "stopping backend child process $($listener.OwningProcess) on port $backendPort"
    Stop-Process -Id $listener.OwningProcess -Force
  }
}

function Invoke-LoggedCommand($label, $filePath, [string[]] $argumentList, $workingDirectory, $logPath, $environment = @{}) {
  Write-Step "running $label; log=$logPath"
  $previous = Set-TemporaryEnvironment $environment
  Push-Location $workingDirectory
  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    & $filePath @argumentList *>&1 | Tee-Object -FilePath $logPath
    $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    if ($exitCode -ne 0) {
      throw "$label failed with exit code $exitCode. See $logPath"
    }
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
    Pop-Location
    Restore-TemporaryEnvironment $previous
  }
}

try {
  Write-Step "artifacts=$runDir"
  @(
    "timestamp=$stamp"
    "backend_url=$backendBaseUrl"
    "backend_port=$backendPort"
    "postgres_jdbc_url=$PostgresJdbcUrl"
    "management_health_redis_enabled=false"
    "is_windows=$(Test-IsWindows)"
  ) | Set-Content -Path $metadataLog

  if (Test-BackendHealth) {
    Write-Step "reusing existing backend: $healthUrl"
  } elseif ($SkipServiceStart) {
    throw "Backend is not healthy at $healthUrl and -SkipServiceStart was set"
  } else {
    Invoke-LoggedCommand `
      'central Compose health' `
      (Get-PowerShellCommand) `
      @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repoRoot 'qa/central-compose-health.ps1')) `
      $repoRoot `
      $composeHealthLog

    $backendProcess = Start-BackendService
    $backendStartedByScript = $true
    Wait-BackendHealth
  }

  Invoke-LoggedCommand `
    'API smoke' `
    (Get-PowerShellCommand) `
    @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $repoRoot 'qa/api-smoke.ps1'), '-BaseUrl', $backendBaseUrl) `
    $repoRoot `
    $apiSmokeLog

  Invoke-LoggedCommand `
    'real-backend Playwright' `
    (Get-NpmCommand) `
    @('run', 'e2e', '--', 'tests/e2e/real-backend.spec.ts') `
    (Join-Path $repoRoot 'apps/web') `
    $playwrightLog `
    @{
      REAL_BACKEND_E2E = '1'
      REAL_BACKEND_BASE_URL = $backendBaseUrl
      NUXT_PUBLIC_API_BASE_URL = $backendBaseUrl
      NUXT_DEV_PORT = '3010'
      PLAYWRIGHT_BASE_URL = 'http://127.0.0.1:3010'
    }

  Write-Output "REAL_BACKEND_E2E_PASS artifacts=$runDir"
} finally {
  Stop-OwnedBackendService
}
