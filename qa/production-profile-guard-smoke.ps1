$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$artifactDir = $env:PRODUCTION_PROFILE_GUARD_ARTIFACT_DIR
if ([string]::IsNullOrWhiteSpace($artifactDir)) {
  $artifactDir = 'qa/artifacts/production-profile-guard/local'
}
if (-not [System.IO.Path]::IsPathRooted($artifactDir)) {
  $artifactDir = Join-Path $repoRoot $artifactDir
}

$expectedMessage = 'production-like runtime profiles require postgres to avoid in-memory persistence defaults'
$logPath = Join-Path $artifactDir 'bootrun.log'

function Get-GradleCommand {
  $isWindows = $PSVersionTable.Platform -eq 'Win32NT' -or $env:OS -eq 'Windows_NT'
  if ($isWindows) {
    return Join-Path $repoRoot 'gradlew.bat'
  }
  return Join-Path $repoRoot 'gradlew'
}

function Invoke-ExpectedFailingBootRun {
  $gradle = Get-GradleCommand
  $arguments = @(
    ':backend:boot:bootRun',
    '--args=--spring.profiles.active=production --spring.main.web-application-type=none'
  )

  Push-Location $repoRoot
  try {
    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    if (Get-Variable -Name PSNativeCommandUseErrorActionPreference -Scope Global -ErrorAction SilentlyContinue) {
      $previousNativePreference = $global:PSNativeCommandUseErrorActionPreference
      $global:PSNativeCommandUseErrorActionPreference = $false
    } else {
      $previousNativePreference = $null
    }

    $output = & $gradle @arguments *>&1
    $exitCode = $LASTEXITCODE
  } finally {
    $ErrorActionPreference = $previousErrorPreference
    if ($null -ne $previousNativePreference) {
      $global:PSNativeCommandUseErrorActionPreference = $previousNativePreference
    }
    Pop-Location
  }

  return @{
    ExitCode = $exitCode
    Output = ($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
  }
}

New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
$result = Invoke-ExpectedFailingBootRun
$result.Output | Set-Content -Path $logPath -Encoding UTF8

if ($result.ExitCode -eq 0) {
  throw "production profile guard smoke expected bootRun to fail, but it succeeded. Log: $logPath"
}

if (-not $result.Output.Contains($expectedMessage)) {
  throw "production profile guard smoke failed without the expected guard message. Log: $logPath"
}

Write-Output "Production profile guard bootRun failed as expected. Log: $logPath"
Write-Output 'PRODUCTION_PROFILE_GUARD_SMOKE_PASS'
