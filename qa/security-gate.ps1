$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$artifactDir = $env:SECURITY_ARTIFACT_DIR
if ([string]::IsNullOrWhiteSpace($artifactDir)) {
  $artifactDir = Join-Path $repoRoot 'qa/artifacts/security'
}
if (-not [System.IO.Path]::IsPathRooted($artifactDir)) {
  $artifactDir = Join-Path $repoRoot $artifactDir
}
$artifactDir = [System.IO.Path]::GetFullPath($artifactDir)
$allowedArtifactRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'qa/artifacts'))
if (-not $artifactDir.StartsWith($allowedArtifactRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
  throw "SECURITY_ARTIFACT_DIR must be under qa/artifacts: $artifactDir"
}
$allowlistPath = $env:SECURITY_ALLOWLIST_PATH
if ([string]::IsNullOrWhiteSpace($allowlistPath)) {
  $allowlistPath = Join-Path $repoRoot 'qa/security-allowlist.json'
}
if (-not [System.IO.Path]::IsPathRooted($allowlistPath)) {
  $allowlistPath = Join-Path $repoRoot $allowlistPath
}

function Get-GradleCommand {
  $isWindows = $PSVersionTable.Platform -eq 'Win32NT' -or $env:OS -eq 'Windows_NT'
  if ($isWindows) {
    return Join-Path $repoRoot 'gradlew.bat'
  }
  return Join-Path $repoRoot 'gradlew'
}

function Invoke-CapturedCommand($file, [string[]] $arguments, $outputPath) {
  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    $output = & $file @arguments *>&1
    $exitCode = $LASTEXITCODE
    $output | Out-File -FilePath $outputPath -Encoding utf8
    return [pscustomobject]@{
      ExitCode = $exitCode
      Output = ($output -join [Environment]::NewLine)
    }
  } finally {
    $ErrorActionPreference = $previousErrorActionPreference
  }
}

function Read-Allowlist {
  if (-not (Test-Path $allowlistPath)) {
    throw 'security-allowlist.json is missing'
  }
  $json = Get-Content -Path $allowlistPath -Raw | ConvertFrom-Json
  if ($null -eq $json.allowlist) {
    throw 'security-allowlist.json must contain allowlist array'
  }
  return @($json.allowlist)
}

function Test-AllowlistEntry($entry) {
  $required = @('id', 'package', 'ecosystem', 'severity', 'owner', 'reason', 'expiresOn')
  foreach ($field in $required) {
    if ([string]::IsNullOrWhiteSpace([string]$entry.$field)) {
      return "allowlist entry is missing $field"
    }
  }
  try {
    $expiry = [DateTime]::ParseExact([string]$entry.expiresOn, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
  } catch {
    return "allowlist entry has invalid expiresOn: $($entry.expiresOn)"
  }
  if ($expiry.Date -lt (Get-Date).Date) {
    return "allowlist entry expired: $($entry.id) $($entry.package)"
  }
  return $null
}

function Test-IsAllowlisted($finding, $allowlist) {
  foreach ($entry in $allowlist) {
    if (
      [string]$entry.id -eq [string]$finding.id -and
      [string]$entry.package -eq [string]$finding.package -and
      [string]$entry.ecosystem -eq [string]$finding.ecosystem -and
      [string]$entry.severity -eq [string]$finding.severity
    ) {
      return $true
    }
  }
  return $false
}

function Convert-NpmAuditFindings($audit) {
  $findings = @()
  if ($null -eq $audit.vulnerabilities) {
    return $findings
  }
  foreach ($property in $audit.vulnerabilities.PSObject.Properties) {
    $vulnerability = $property.Value
    foreach ($via in @($vulnerability.via)) {
      if ($via -is [string]) {
        continue
      }
      $findings += [pscustomobject]@{
        id = if ($via.url) { [string]$via.url } else { [string]$via.source }
        package = [string]$property.Name
        ecosystem = 'npm'
        severity = [string]$via.severity
        title = [string]$via.title
        range = [string]$via.range
        fixAvailable = $vulnerability.fixAvailable
      }
    }
  }
  return $findings
}

function Convert-GradleDependenciesToCycloneDx($dependencyText) {
  $seen = @{}
  $components = @()
  foreach ($line in $dependencyText -split "\r?\n") {
    $cleanLine = [regex]::Replace($line, '\(requested [^)]+\)', '')
    foreach ($match in [regex]::Matches($cleanLine, '([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?:\s*->\s*([A-Za-z0-9_.-]+))?')) {
      $group = $match.Groups[1].Value
      $name = $match.Groups[2].Value
      $version = if ($match.Groups[4].Success) { $match.Groups[4].Value } else { $match.Groups[3].Value }
      $key = "${group}:${name}:${version}"
      if ($seen.ContainsKey($key)) {
        continue
      }
      $seen[$key] = $true
      $components += [ordered]@{
        type = 'library'
        group = $group
        name = $name
        version = $version
        purl = "pkg:maven/$group/$name@$version"
      }
    }
  }
  return [ordered]@{
    bomFormat = 'CycloneDX'
    specVersion = '1.5'
    version = 1
    metadata = [ordered]@{
      timestamp = (Get-Date).ToUniversalTime().ToString('o')
      component = [ordered]@{
        type = 'application'
        name = 'discord-clone-backend'
        version = '0.1.0-SNAPSHOT'
      }
    }
    components = $components
  }
}

function Read-VulnerabilityFindings($path) {
  if (-not (Test-Path $path)) {
    return @()
  }
  $json = Get-Content -Path $path -Raw | ConvertFrom-Json
  if ($null -eq $json.vulnerabilities) {
    return @()
  }
  return @($json.vulnerabilities)
}

function Read-JsonObjectArtifact($path) {
  $raw = Get-Content -Path $path -Raw
  $start = $raw.IndexOf('{')
  $end = $raw.LastIndexOf('}')
  if ($start -lt 0 -or $end -lt $start) {
    throw "JSON artifact does not contain an object: $path"
  }
  return $raw.Substring($start, $end - $start + 1) | ConvertFrom-Json
}

Push-Location $repoRoot
try {
  if (Test-Path $artifactDir) {
    Remove-Item -LiteralPath $artifactDir -Recurse -Force
  }
  New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

  $allowlist = Read-Allowlist
  $policyFailures = @()
  foreach ($entry in $allowlist) {
    $allowlistError = Test-AllowlistEntry $entry
    if ($null -ne $allowlistError) {
      $policyFailures += $allowlistError
    }
  }

  if ($env:SECURITY_GATE_VALIDATE_POLICY_ONLY -eq 'true') {
    if ($policyFailures.Count -gt 0) {
      $policyFailures | Out-File -FilePath (Join-Path $artifactDir 'security-policy-failures.txt') -Encoding utf8
      throw 'security allowlist policy failed'
    }
    Write-Output 'SECURITY_GATE_POLICY_PASS'
    return
  }

  $npmAuditPath = Join-Path $artifactDir 'frontend-vulnerabilities.json'
  $npmAudit = Invoke-CapturedCommand 'npm' @('audit', '--json', '--audit-level=high') $npmAuditPath
  $npmAuditJson = Read-JsonObjectArtifact $npmAuditPath
  if ($null -ne $npmAuditJson.error) {
    throw 'npm audit endpoint failed'
  }
  $frontendFindings = Convert-NpmAuditFindings $npmAuditJson

  $npmSbomRawPath = Join-Path $artifactDir 'frontend-sbom.npm.raw.json'
  $npmSbom = Invoke-CapturedCommand 'npm' @('sbom', '--sbom-format', 'cyclonedx', '--workspaces') $npmSbomRawPath
  if ($npmSbom.ExitCode -ne 0) {
    throw 'frontend native workspace SBOM failed'
  }
  $npmLockfileSbomPath = Join-Path $artifactDir 'frontend-sbom.npm.lockfile.json'
  $npmLockfileSbom = Invoke-CapturedCommand 'npm' @('sbom', '--sbom-format', 'cyclonedx', '--package-lock-only', '--omit=peer', '--omit=optional') $npmLockfileSbomPath
  Copy-Item -Path $npmSbomRawPath -Destination (Join-Path $artifactDir 'frontend-sbom.json') -Force
  $frontendSbom = Get-Content -Path (Join-Path $artifactDir 'frontend-sbom.json') -Raw | ConvertFrom-Json
  $frontendSbom | ConvertTo-Json -Depth 20 | Out-File -FilePath (Join-Path $artifactDir 'frontend-sbom.json') -Encoding utf8
  $frontendOsvPath = Join-Path $artifactDir 'frontend-osv-vulnerabilities.json'
  $frontendOsv = Invoke-CapturedCommand 'node' @('qa/security-osv-scan.mjs', '--sbom', (Join-Path $artifactDir 'frontend-sbom.json'), '--ecosystem', 'npm', '--output', $frontendOsvPath) (Join-Path $artifactDir 'frontend-osv.log')

  $backendDependenciesPath = Join-Path $artifactDir 'backend-dependencies.txt'
  $gradle = Invoke-CapturedCommand (Get-GradleCommand) @(':backend:boot:dependencies', '--configuration', 'runtimeClasspath') $backendDependenciesPath
  $backendSbom = Convert-GradleDependenciesToCycloneDx $gradle.Output
  $backendSbom | ConvertTo-Json -Depth 20 | Out-File -FilePath (Join-Path $artifactDir 'backend-sbom.json') -Encoding utf8
  $backendVulnerabilitiesPath = Join-Path $artifactDir 'backend-vulnerabilities.json'
  $backendOsv = Invoke-CapturedCommand 'node' @('qa/security-osv-scan.mjs', '--sbom', (Join-Path $artifactDir 'backend-sbom.json'), '--ecosystem', 'Maven', '--output', $backendVulnerabilitiesPath) (Join-Path $artifactDir 'backend-osv.log')

  $blockingFindings = @()
  foreach ($finding in $frontendFindings) {
    if ($finding.severity -in @('high', 'critical') -and -not (Test-IsAllowlisted $finding $allowlist)) {
      $blockingFindings += $finding
    }
  }
  $frontendOsvFindings = Read-VulnerabilityFindings $frontendOsvPath
  $backendFindings = Read-VulnerabilityFindings $backendVulnerabilitiesPath
  foreach ($finding in @($frontendOsvFindings + $backendFindings)) {
    if ($finding.severity -in @('high', 'critical', 'unknown') -and -not (Test-IsAllowlisted $finding $allowlist)) {
      $blockingFindings += $finding
    }
  }

  $summary = @()
  $summary += '# Security Gate Summary'
  $summary += ''
  $summary += "Date: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss zzz'))"
  $summary += ''
  $summary += '## Results'
  $summary += ''
  $summary += "| Check | Result | Notes |"
  $summary += "| --- | --- | --- |"
  $summary += "| npm audit | PASS | high/critical blocking findings: $($blockingFindings.Count); total npm findings: $($frontendFindings.Count) |"
  $summary += "| frontend SBOM | PASS | native workspace npm sbom components: $($frontendSbom.components.Count) |"
  $summary += "| frontend native package-lock SBOM | $(if ($npmLockfileSbom.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }) | npm package-lock-only omit peer/optional artifact generated. |"
  $summary += "| backend dependency inventory | PASS | Gradle runtimeClasspath captured; components: $($backendSbom.components.Count) |"
  $summary += "| frontend OSV vulnerability database | $(if ($frontendOsv.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }) | OSV findings: $($frontendOsvFindings.Count) |"
  $summary += "| backend vulnerability database | $(if ($backendOsv.ExitCode -eq 0) { 'PASS' } else { 'FAIL' }) | OSV Maven findings: $($backendFindings.Count) |"
  $summary += "| allowlist policy | $(if ($policyFailures.Count -eq 0) { 'PASS' } else { 'FAIL' }) | entries: $($allowlist.Count) |"
  $summary += ''
  if ($frontendFindings.Count -gt 0) {
    $summary += '## Frontend Findings'
    $summary += ''
    foreach ($finding in $frontendFindings) {
      $summary += "- [$($finding.severity)] $($finding.package): $($finding.title) ($($finding.id))"
    }
    $summary += ''
  }
  if ($policyFailures.Count -gt 0) {
    $summary += '## Policy Failures'
    $summary += ''
    foreach ($failure in $policyFailures) {
      $summary += "- $failure"
    }
    $summary += ''
  }
  $summary += '## Artifacts'
  $summary += ''
  $summary += '- `frontend-vulnerabilities.json`'
  $summary += '- `frontend-osv-vulnerabilities.json`'
  $summary += '- `frontend-sbom.json`'
  $summary += '- `frontend-sbom.npm.raw.json`'
  $summary += '- `frontend-sbom.npm.lockfile.json`'
  $summary += '- `backend-vulnerabilities.json`'
  $summary += '- `backend-sbom.json`'
  $summary += '- `backend-dependencies.txt`'
  $summary | Out-File -FilePath (Join-Path $artifactDir 'security-gate-summary.md') -Encoding utf8

  if ($policyFailures.Count -gt 0 -or $blockingFindings.Count -gt 0 -or $npmAudit.ExitCode -gt 1 -or $npmLockfileSbom.ExitCode -ne 0 -or $gradle.ExitCode -ne 0 -or $frontendOsv.ExitCode -ne 0 -or $backendOsv.ExitCode -ne 0) {
    throw 'security gate failed; see qa/artifacts/security/security-gate-summary.md'
  }

  Write-Output 'SECURITY_GATE_PASS'
} finally {
  Pop-Location
}
