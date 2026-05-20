param(
  [Parameter(Mandatory = $true)]
  [string] $TaskId,
  [Parameter(Mandatory = $true)]
  [string[]] $Paths,
  [string[]] $PlanDesignPaths = @(),
  [string[]] $TestsRun = @(),
  [string[]] $ArtifactPaths = @(),
  [string[]] $KnownResidualRisks = @(),
  [string] $OutputDir = 'qa/artifacts/review-packets'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$packetRoot = if ([System.IO.Path]::IsPathRooted($OutputDir)) { $OutputDir } else { Join-Path $repoRoot $OutputDir }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$packetPath = Join-Path $packetRoot "$stamp-$TaskId-review-packet.md"

New-Item -ItemType Directory -Force -Path $packetRoot | Out-Null

$diffStat = & git diff --stat -- $Paths
if ($LASTEXITCODE -ne 0) {
  throw 'git diff --stat failed while creating review packet.'
}

$diff = & git diff -- $Paths
if ($LASTEXITCODE -ne 0) {
  throw 'git diff -- failed while creating review packet.'
}

$planDesignLines = if ($PlanDesignPaths.Count -eq 0) { '- none provided' } else { ($PlanDesignPaths | ForEach-Object { "- $_" }) -join "`r`n" }
$changedFileLines = if ($Paths.Count -eq 0) { '- none provided' } else { ($Paths | ForEach-Object { "- $_" }) -join "`r`n" }
$testLines = if ($TestsRun.Count -eq 0) { '- none provided' } else { ($TestsRun | ForEach-Object { "- $_" }) -join "`r`n" }
$artifactLines = if ($ArtifactPaths.Count -eq 0) { '- none provided' } else { ($ArtifactPaths | ForEach-Object { "- $_" }) -join "`r`n" }
$riskLines = if ($KnownResidualRisks.Count -eq 0) { '- none provided' } else { ($KnownResidualRisks | ForEach-Object { "- $_" }) -join "`r`n" }

$content = @"
# Diff-Only Review Packet

Task ID: $TaskId

## Plan/Design Paths

$planDesignLines

## Changed Files

$changedFileLines

## git diff --stat

~~~text
$($diffStat -join "`r`n")
~~~

## git diff -- <task-owned paths>

~~~diff
$($diff -join "`r`n")
~~~

## Tests Run

$testLines

## Artifact Paths

$artifactLines

## Known Residual Risks

$riskLines

## Forbidden Context

- Do not include implementer scratch notes.
- Do not include implementation-chat conclusions.
- Do not include full raw failure logs.

## Review Return Format

- P0/P1/P2 findings
- file:line
- reproduction command
- required fix or acceptable deferral
"@

Set-Content -Path $packetPath -Value $content -Encoding UTF8
Write-Output "REVIEW_PACKET_CREATED $packetPath"
