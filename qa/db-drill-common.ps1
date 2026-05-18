$ErrorActionPreference = 'Stop'

function Test-IsWindows {
  return [System.Environment]::OSVersion.Platform -eq 'Win32NT'
}

function Resolve-RepoRoot {
  return Split-Path -Parent $PSScriptRoot
}

function Write-DrillStep([string] $Message) {
  Write-Output "[db-drill] $Message"
}

function Assert-CommandAvailable([string] $CommandName, [string] $InstallHint) {
  $command = Get-Command $CommandName -ErrorAction SilentlyContinue
  if ($null -eq $command) {
    throw "$CommandName not found. Install PostgreSQL client tools and ensure they are on PATH. $InstallHint"
  }
}

function ConvertFrom-JdbcPostgresUrl([string] $JdbcUrl) {
  if ([string]::IsNullOrWhiteSpace($JdbcUrl) -or -not $JdbcUrl.StartsWith('jdbc:postgresql://')) {
    throw "Expected a PostgreSQL JDBC URL such as jdbc:postgresql://127.0.0.1:5432/discord"
  }

  $withoutPrefix = $JdbcUrl.Substring('jdbc:postgresql://'.Length)
  $withoutQuery = $withoutPrefix.Split('?')[0]
  $slashIndex = $withoutQuery.IndexOf('/')
  if ($slashIndex -lt 1 -or $slashIndex -eq ($withoutQuery.Length - 1)) {
    throw "PostgreSQL JDBC URL must include host and database name"
  }

  $hostPort = $withoutQuery.Substring(0, $slashIndex)
  $database = $withoutQuery.Substring($slashIndex + 1)
  $dbHost = $hostPort
  $port = '5432'
  if ($hostPort.Contains(':')) {
    $parts = $hostPort.Split(':', 2)
    $dbHost = $parts[0]
    $port = $parts[1]
  }

  return [pscustomobject]@{
    Host = $dbHost
    Port = $port
    Database = $database
    Redacted = "jdbc:postgresql://$dbHost`:$port/$database"
  }
}

function Assert-SafeJdbcUrl([string] $JdbcUrl, [switch] $AllowNonLocal) {
  $parsed = ConvertFrom-JdbcPostgresUrl $JdbcUrl
  $lower = $JdbcUrl.ToLowerInvariant()
  if ($lower.Contains('production') -or $lower.Contains('prod')) {
    throw "Refusing production-like database URL: $($parsed.Redacted)"
  }

  $localHosts = @('127.0.0.1', 'localhost', '::1')
  if (-not $AllowNonLocal -and -not $localHosts.Contains($parsed.Host.ToLowerInvariant())) {
    throw "Refusing non-local database host '$($parsed.Host)'. Pass -AllowNonLocal only for an explicitly approved drill target."
  }

  return $parsed
}

function Assert-SourceAndTargetDiffer([string] $SourceJdbcUrl, [string] $TargetJdbcUrl) {
  if (-not [string]::IsNullOrWhiteSpace($SourceJdbcUrl) -and $SourceJdbcUrl.Trim() -eq $TargetJdbcUrl.Trim()) {
    throw 'refusing to restore into source database: source and target JDBC URLs are identical'
  }
}

function Set-TemporaryEnvironment($Environment) {
  $previous = @{}
  foreach ($key in $Environment.Keys) {
    $previous[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
    [Environment]::SetEnvironmentVariable($key, [string] $Environment[$key], 'Process')
  }
  return $previous
}

function Restore-TemporaryEnvironment($Previous) {
  foreach ($key in $Previous.Keys) {
    [Environment]::SetEnvironmentVariable($key, $Previous[$key], 'Process')
  }
}

function Invoke-LoggedCommand($Label, $FilePath, [string[]] $ArgumentList, $WorkingDirectory, $LogPath, $Environment = @{}) {
  Write-DrillStep "running $Label; log=$LogPath"
  $previous = Set-TemporaryEnvironment $Environment
  Push-Location $WorkingDirectory
  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    & $FilePath @ArgumentList *>&1 | Tee-Object -FilePath $LogPath
    $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
    if ($exitCode -ne 0) {
      throw "$Label failed with exit code $exitCode. See $LogPath"
    }
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
    Pop-Location
    Restore-TemporaryEnvironment $previous
  }
}

function Get-PowerShellCommand {
  if (Test-IsWindows) {
    return 'powershell'
  }
  return 'pwsh'
}

function Get-GradleWrapper {
  $repoRoot = Resolve-RepoRoot
  if (Test-IsWindows) {
    return Join-Path $repoRoot 'gradlew.bat'
  }
  return Join-Path $repoRoot 'gradlew'
}

function Remove-OldDrillArtifacts([string] $ArtifactRoot, [int] $KeepLatest = 5) {
  if (-not (Test-Path $ArtifactRoot)) {
    return
  }

  Get-ChildItem -Path $ArtifactRoot -Directory |
    Sort-Object Name -Descending |
    Select-Object -Skip $KeepLatest |
    Remove-Item -Recurse -Force
}
