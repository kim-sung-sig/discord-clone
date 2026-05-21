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

function Invoke-PostgresQuery([string] $JdbcUrl, [string] $PostgresUser, [string] $PostgresPassword, [string] $PostgresCliContainer, [string] $Query) {
  $parsed = ConvertFrom-JdbcPostgresUrl $JdbcUrl
  if (-not [string]::IsNullOrWhiteSpace($PostgresCliContainer)) {
    Assert-CommandAvailable 'docker' 'Docker is required when -PostgresCliContainer is used.'
    $arguments = @(
      'exec',
      '-e',
      "PGPASSWORD=$PostgresPassword",
      $PostgresCliContainer,
      'psql',
      '-h',
      '127.0.0.1',
      '-p',
      '5432',
      '-U',
      $PostgresUser,
      '-d',
      $parsed.Database,
      '-t',
      '-A',
      '-v',
      'ON_ERROR_STOP=1',
      '-c',
      $Query
    )
    $output = & docker @arguments
  } else {
    Assert-CommandAvailable 'psql' 'Windows users can install PostgreSQL or add the PostgreSQL bin directory to PATH.'
    $previousPassword = [Environment]::GetEnvironmentVariable('PGPASSWORD', 'Process')
    [Environment]::SetEnvironmentVariable('PGPASSWORD', $PostgresPassword, 'Process')
    try {
      $output = & psql -h $parsed.Host -p $parsed.Port -U $PostgresUser -d $parsed.Database -t -A -v ON_ERROR_STOP=1 -c $Query
    } finally {
      [Environment]::SetEnvironmentVariable('PGPASSWORD', $previousPassword, 'Process')
    }
  }

  $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
  if ($exitCode -ne 0) {
    throw "PostgreSQL query failed with exit code $exitCode for $($parsed.Redacted)"
  }
  return @($output)
}

function Quote-PostgresIdentifier([string] $Identifier) {
  return '"' + $Identifier.Replace('"', '""') + '"'
}

function Write-DatabaseSnapshotHash([string] $JdbcUrl, [string] $PostgresUser, [string] $PostgresPassword, [string] $PostgresCliContainer, [string] $OutputPath) {
  $parsed = ConvertFrom-JdbcPostgresUrl $JdbcUrl
  $tableQuery = "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history' ORDER BY tablename;"
  $tableNames = Invoke-PostgresQuery $JdbcUrl $PostgresUser $PostgresPassword $PostgresCliContainer $tableQuery |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) }

  $lines = @(
    '# db_snapshot_hash_v1'
    "jdbc_url=$($parsed.Redacted)"
    'scope=public_tables_excluding_flyway_schema_history'
    'table|row_count|row_hash'
  )

  foreach ($tableName in $tableNames) {
    $table = [string] $tableName
    $tableLiteral = $table.Replace("'", "''")
    $tableIdentifier = Quote-PostgresIdentifier $table
    $hashQuery = @"
SELECT '$tableLiteral'
  || '|'
  || count(*)::text
  || '|'
  || COALESCE(md5(string_agg(row_to_json(snapshot_row)::text, E'\n' ORDER BY row_to_json(snapshot_row)::text)), md5(''))
FROM (SELECT * FROM public.$tableIdentifier) snapshot_row;
"@
    $hashLine = Invoke-PostgresQuery $JdbcUrl $PostgresUser $PostgresPassword $PostgresCliContainer $hashQuery |
      Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
      Select-Object -First 1
    if ([string]::IsNullOrWhiteSpace($hashLine)) {
      throw "Snapshot hash query returned no row for table $table"
    }
    $lines += [string] $hashLine
  }

  $lines | Set-Content -Path $OutputPath
  Write-DrillStep "wrote database snapshot hash: $OutputPath"
}

function Compare-DatabaseSnapshotHashes([string] $SourcePath, [string] $RestoredPath, [string] $DiffPath) {
  $sourceLines = Get-Content -Path $SourcePath | Where-Object { $_ -match '^[^#=]+\|[0-9]+\|[a-f0-9]{32}$' }
  $restoredLines = Get-Content -Path $RestoredPath | Where-Object { $_ -match '^[^#=]+\|[0-9]+\|[a-f0-9]{32}$' }
  $diff = Compare-Object -ReferenceObject $sourceLines -DifferenceObject $restoredLines
  if ($null -ne $diff) {
    $diff | Out-String | Set-Content -Path $DiffPath
    throw "Restored database snapshot hash mismatch. See $DiffPath"
  }
  "snapshot_hash_comparison=PASS" | Set-Content -Path $DiffPath
  Write-DrillStep "database snapshot hashes match: $SourcePath -> $RestoredPath"
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
