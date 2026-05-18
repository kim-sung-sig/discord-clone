param(
  [string] $MigrationDir = 'backend/boot/src/main/resources/db/migration'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolvedMigrationDir = if ([System.IO.Path]::IsPathRooted($MigrationDir)) { $MigrationDir } else { Join-Path $repoRoot $MigrationDir }

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $resolvedMigrationDir) "Migration directory not found: $resolvedMigrationDir"

$destructivePatterns = @(
  'DROP\s+TABLE',
  'DROP\s+COLUMN',
  'TRUNCATE',
  'DELETE\s+FROM',
  'ALTER\s+TABLE.*RENAME\s+COLUMN'
)

$findings = @()
foreach ($file in Get-ChildItem -Path $resolvedMigrationDir -Filter '*.sql' -File | Sort-Object Name) {
  $content = Get-Content -Path $file.FullName -Raw
  foreach ($pattern in $destructivePatterns) {
    if ($content -match $pattern -and $content -notmatch 'T39-DESTRUCTIVE-REVIEWED') {
      $findings += [pscustomobject]@{
        file = $file.Name
        pattern = $pattern
      }
    }
  }
}

if ($findings.Count -gt 0) {
  $json = $findings | ConvertTo-Json -Compress
  throw "Destructive migration pattern requires explicit review marker T39-DESTRUCTIVE-REVIEWED: $json"
}

Write-Output 'MIGRATION_GUARD_PASS'
