param(
  [int] $Port = 3025,
  [string] $ArtifactDir = $env:DASHBOARD_GUARD_HEALTH_ARTIFACT_DIR,
  [int] $StartupTimeoutSeconds = 120,
  [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$webRoot = Join-Path $repoRoot 'apps/web'
if ([string]::IsNullOrWhiteSpace($ArtifactDir)) {
  $ArtifactDir = 'qa/artifacts/dashboard-guard-health/local'
}
if (-not [System.IO.Path]::IsPathRooted($ArtifactDir)) {
  $ArtifactDir = Join-Path $repoRoot $ArtifactDir
}

$buildLog = Join-Path $ArtifactDir 'nuxt-build.log'
$serverLog = Join-Path $ArtifactDir 'nuxt-server.log'
$serverErrorLog = Join-Path $ArtifactDir 'nuxt-server.err.log'
$healthLog = Join-Path $ArtifactDir 'guard-health.json'
$metadataLog = Join-Path $ArtifactDir 'run-metadata.txt'
$healthUrl = "http://127.0.0.1:$Port/api/security/dashboard-guard-health"
$serverProcess = $null

function Test-IsWindows {
  return [System.Environment]::OSVersion.Platform -eq 'Win32NT'
}

function Get-NpmCommand {
  if (Test-IsWindows) {
    return 'npm.cmd'
  }
  return 'npm'
}

function Get-NodeCommand {
  if (Test-IsWindows) {
    return 'node.exe'
  }
  return 'node'
}

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
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

function Invoke-LoggedCommand($label, $filePath, [string[]] $argumentList, $workingDirectory, $logPath) {
  $errorLogPath = [System.IO.Path]::ChangeExtension($logPath, '.err.log')
  $startParams = @{
    FilePath = $filePath
    ArgumentList = $argumentList
    WorkingDirectory = $workingDirectory
    RedirectStandardOutput = $logPath
    RedirectStandardError = $errorLogPath
    Wait = $true
    PassThru = $true
  }
  if (Test-IsWindows) {
    $startParams.WindowStyle = 'Hidden'
  }
  $process = Start-Process @startParams
  if ($process.ExitCode -ne 0) {
    throw "$label failed with exit code $($process.ExitCode). See $logPath and $errorLogPath"
  }
}

function Read-ErrorResponseContent($response) {
  if ($null -eq $response) {
    return ''
  }
  $contentProperty = $response.PSObject.Properties['Content']
  $content = if ($null -eq $contentProperty) { $null } else { $contentProperty.Value }
  if ($null -ne $content -and $content.PSObject.Methods['ReadAsStringAsync']) {
    return $content.ReadAsStringAsync().GetAwaiter().GetResult()
  }
  if ($response.PSObject.Methods['GetResponseStream']) {
    $stream = $response.GetResponseStream()
    if ($null -eq $stream) {
      return ''
    }
    $reader = [System.IO.StreamReader]::new($stream)
    try {
      return $reader.ReadToEnd()
    } finally {
      $reader.Dispose()
    }
  }
  return ''
}

function Invoke-GuardHealthRequest {
  try {
    $response = Invoke-WebRequest -UseBasicParsing -Method GET -Uri $healthUrl -TimeoutSec 5
    return [pscustomobject]@{
      StatusCode = [int] $response.StatusCode
      Content = [string] $response.Content
    }
  } catch {
    $response = $_.Exception.Response
    if ($null -eq $response) {
      throw
    }
    return [pscustomobject]@{
      StatusCode = [int] $response.StatusCode
      Content = Read-ErrorResponseContent $response
    }
  }
}

function Wait-GuardHealthEndpoint {
  $deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
  $lastError = $null
  while ((Get-Date) -lt $deadline) {
    try {
      return Invoke-GuardHealthRequest
    } catch {
      $lastError = $_
      if ($null -ne $serverProcess -and $serverProcess.HasExited) {
        throw "Nuxt server exited before guard health became reachable. See $serverLog and $serverErrorLog"
      }
      Start-Sleep -Seconds 2
    }
  }
  throw "Dashboard guard health endpoint did not become reachable within $StartupTimeoutSeconds seconds at $healthUrl. Last error: $lastError"
}

function Start-NuxtServer {
  $serverPath = Join-Path $webRoot '.output/server/index.mjs'
  Assert (Test-Path $serverPath) "Nuxt server output is missing: $serverPath"

  $environment = @{
    NODE_ENV = 'production'
    NITRO_HOST = '127.0.0.1'
    HOST = '127.0.0.1'
    NITRO_PORT = "$Port"
    PORT = "$Port"
  }

  $previous = Set-TemporaryEnvironment $environment
  try {
    $startParams = @{
      FilePath = Get-NodeCommand
      ArgumentList = @($serverPath)
      WorkingDirectory = $webRoot
      RedirectStandardOutput = $serverLog
      RedirectStandardError = $serverErrorLog
      PassThru = $true
    }
    if (Test-IsWindows) {
      $startParams.WindowStyle = 'Hidden'
    }
    return Start-Process @startParams
  } finally {
    Restore-TemporaryEnvironment $previous
  }
}

New-Item -ItemType Directory -Force -Path $ArtifactDir | Out-Null
@(
  "port=$Port"
  "health_url=$healthUrl"
  "node_env=production"
  "operator_token_configured=$(-not [string]::IsNullOrWhiteSpace($env:NUXT_SECURITY_DASHBOARD_TOKEN))"
) | Set-Content -Path $metadataLog -Encoding UTF8

try {
  if (-not $SkipBuild) {
    Invoke-LoggedCommand 'Nuxt production build' (Get-NpmCommand) @('run', 'build', '--workspace', '@discord-clone/web') $repoRoot $buildLog
  }

  $serverProcess = Start-NuxtServer
  $healthResponse = Wait-GuardHealthEndpoint
  $healthResponse.Content | Set-Content -Path $healthLog -Encoding UTF8

  $health = $null
  if (-not [string]::IsNullOrWhiteSpace($healthResponse.Content)) {
    $health = $healthResponse.Content | ConvertFrom-Json
  }

  if ($healthResponse.StatusCode -ne 200) {
    throw "Dashboard guard health smoke failed with HTTP $($healthResponse.StatusCode) and status '$($health.status)'. Production deployments must not be fail-closed. See $healthLog"
  }

  Assert ($health.status -ne 'fail-closed') "Dashboard guard health smoke found fail-closed status. See $healthLog"
  Assert ($health.status -eq 'ready') "Dashboard guard health smoke expected ready status, got '$($health.status)'. See $healthLog"
  Assert ($health.configured -eq $true) "Dashboard guard health smoke expected configured guard. See $healthLog"
  Assert ($health.requireConfiguredGuard -eq $true) "Dashboard guard health smoke expected production guard enforcement. See $healthLog"

  if (-not [string]::IsNullOrWhiteSpace($env:NUXT_SECURITY_DASHBOARD_TOKEN)) {
    Assert (-not $healthResponse.Content.Contains($env:NUXT_SECURITY_DASHBOARD_TOKEN)) 'Guard health response exposed NUXT_SECURITY_DASHBOARD_TOKEN'
  }

  Write-Output "Dashboard guard health ready. Artifact: $healthLog"
  Write-Output 'DASHBOARD_GUARD_HEALTH_SMOKE_PASS'
} finally {
  if ($null -ne $serverProcess -and -not $serverProcess.HasExited) {
    Stop-Process -Id $serverProcess.Id -Force
  }
}
