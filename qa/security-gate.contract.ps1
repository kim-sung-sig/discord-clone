$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$gate = Join-Path $repoRoot 'qa/security-gate.ps1'
$allowlist = Join-Path $repoRoot 'qa/security-allowlist.json'
$workflow = Join-Path $repoRoot '.github/workflows/ci.yml'
$legacyFrontendSbomFallback = Join-Path $repoRoot 'qa/security-frontend-sbom.mjs'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

function Assert-Tracked($relativePath) {
  $tracked = & git -c 'safe.directory=*' -C $repoRoot ls-files -- $relativePath
  Assert (-not [string]::IsNullOrWhiteSpace($tracked)) "security gate dependency must be tracked: $relativePath"
}

Assert (Test-Path $gate) 'security gate script is missing'
Assert (Test-Path $allowlist) 'security allowlist is missing'
Assert (Test-Path $workflow) 'CI workflow is missing'
Assert (-not (Test-Path $legacyFrontendSbomFallback)) 'legacy frontend SBOM fallback utility must be removed'

foreach ($trackedDependency in @(
  'qa/security-gate.ps1',
  'qa/security-gate.contract.ps1',
  'qa/security-osv-scan.mjs',
  'qa/security-allowlist.json'
)) {
  Assert-Tracked $trackedDependency
}

$gateText = Get-Content -Path $gate -Raw
Assert ($gateText.Contains('npm audit')) 'security gate must run npm audit'
Assert ($gateText.Contains('npm sbom')) 'security gate must generate npm SBOM'
Assert (-not $gateText.Contains('security-frontend-sbom.mjs')) 'security gate must not rely on the legacy lockfile SBOM fallback'
Assert (-not $gateText.Contains('frontend-sbom.fallback.log')) 'security gate must not write fallback SBOM logs'
Assert (-not $gateText.Contains('frontend-sbom.npm.error.txt')) 'security gate must not continue after native workspace SBOM failure'
Assert ($gateText.Contains('security-osv-scan.mjs')) 'security gate must run OSV vulnerability scans'
Assert ($gateText.Contains('--workspaces')) 'security gate must generate a native workspace SBOM artifact'
Assert ($gateText.Contains('--package-lock-only')) 'security gate must generate a native package-lock SBOM artifact'
Assert ($gateText.Contains('--omit=peer')) 'security gate native package-lock SBOM must omit peer dependency noise'
Assert ($gateText.Contains('--omit=optional')) 'security gate native package-lock SBOM must omit optional dependency noise'
Assert ($gateText.Contains('gradlew')) 'security gate must collect backend Gradle dependency evidence'
Assert ($gateText.Contains('security-gate-summary.md')) 'security gate must write a stable summary'
Assert ($gateText.Contains('frontend-vulnerabilities.json')) 'security gate must write frontend vulnerability evidence'
Assert ($gateText.Contains('frontend-osv-vulnerabilities.json')) 'security gate must write frontend OSV vulnerability evidence'
Assert ($gateText.Contains('frontend-sbom.npm.raw.json')) 'security gate must write native npm workspace SBOM evidence'
Assert ($gateText.Contains('frontend-sbom.npm.lockfile.json')) 'security gate must write native npm package-lock SBOM evidence'
Assert ($gateText.Contains('backend-vulnerabilities.json')) 'security gate must write backend vulnerability evidence'
Assert ($gateText.Contains('@($frontendOsvFindings) + @($backendFindings)')) 'security gate must combine OSV findings as arrays even when one side has a single finding'
Assert ($gateText.Contains('frontend-sbom.json')) 'security gate must write frontend SBOM'
Assert ($gateText.Contains('backend-sbom.json')) 'security gate must write backend SBOM'
Assert ($gateText.Contains('security-allowlist.json')) 'security gate must evaluate the allowlist'
Assert ($gateText.Contains('SECURITY_ALLOWLIST_PATH')) 'security gate must support policy fixture allowlist path'
Assert ($gateText.Contains('SECURITY_GATE_VALIDATE_POLICY_ONLY')) 'security gate must support fast policy-only validation'
Assert ($gateText.Contains('expiresOn')) 'security gate must enforce allowlist expiry'
Assert ($gateText.Contains('GetFullPath')) 'security gate must normalize artifact paths before deletion'
Assert ($gateText.Contains('SECURITY_ARTIFACT_DIR must be under qa/artifacts')) 'security gate must reject artifact paths outside qa/artifacts'
Assert ($gateText.Contains('[string]$entry.severity -eq [string]$finding.severity')) 'security gate allowlist matching must include severity'
Assert ($gateText.Contains("'unknown'")) 'security gate must fail closed on unknown OSV severity findings'

$packageJson = Get-Content -Path (Join-Path $repoRoot 'package.json') -Raw | ConvertFrom-Json
Assert ($packageJson.devDependencies.commander -eq '^13.1.0') 'root package must pin commander 13 so npm workspace SBOM satisfies @bomb.sh/tab optional peer'

$osvScanner = Join-Path $repoRoot 'qa/security-osv-scan.mjs'
Assert (Test-Path $osvScanner) 'OSV scanner script is missing'
$osvScannerText = Get-Content -Path $osvScanner -Raw
Assert ($osvScannerText.Contains('https://api.osv.dev/v1/querybatch')) 'OSV scanner must use OSV querybatch API'
Assert ($osvScannerText.Contains('https://api.osv.dev/v1/vulns/')) 'OSV scanner must enrich vulnerability details'
Assert ($osvScannerText.Contains('Maven')) 'OSV scanner must support Maven ecosystem'
Assert ($osvScannerText.Contains('npm')) 'OSV scanner must support npm ecosystem'
Assert ($osvScannerText.Contains('cvssBaseScore')) 'OSV scanner must calculate CVSS base scores'
Assert ($osvScannerText.Contains('score >= 9')) 'OSV scanner must map CVSS 9+ to critical'
Assert ($osvScannerText.Contains('score >= 7')) 'OSV scanner must map CVSS 7+ to high'
Assert ($osvScannerText.Contains('AbortSignal.timeout')) 'OSV scanner must bound fetch duration'

$workflowText = Get-Content -Path $workflow -Raw
Assert ($workflowText.Contains('qa-security:')) 'CI workflow must include qa-security job'
Assert ($workflowText.Contains('pwsh qa/security-gate.contract.ps1')) 'CI workflow must run security gate contract'
Assert ($workflowText.Contains('pwsh qa/security-gate.ps1')) 'CI workflow must run security gate'
Assert ($workflowText.Contains('qa/artifacts/security')) 'CI workflow must upload security artifacts'
Assert ($workflowText.Contains('security-gate-artifacts')) 'CI workflow must name security artifacts'
Assert ($workflowText.Contains('timeout-minutes: 20')) 'CI security job must set a timeout'
Assert ($workflowText.Contains('permissions:')) 'CI security job must scope permissions'
Assert ($workflowText.Contains('contents: read')) 'CI security job must only require contents read permission'

$contractArtifactDir = Join-Path $repoRoot 'qa/artifacts/security-contract'
if (Test-Path $contractArtifactDir) {
  Remove-Item -LiteralPath $contractArtifactDir -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $contractArtifactDir | Out-Null

$validAllowlist = Join-Path $contractArtifactDir 'allowlist-valid.json'
'{"allowlist":[]}' | Out-File -FilePath $validAllowlist -Encoding utf8

$previousAllowlistPath = $env:SECURITY_ALLOWLIST_PATH
$previousPolicyOnly = $env:SECURITY_GATE_VALIDATE_POLICY_ONLY
$previousArtifactDir = $env:SECURITY_ARTIFACT_DIR
try {
  $env:SECURITY_ALLOWLIST_PATH = $validAllowlist
  $env:SECURITY_GATE_VALIDATE_POLICY_ONLY = 'true'
  $env:SECURITY_ARTIFACT_DIR = Join-Path $contractArtifactDir 'valid-artifacts'
  $validOutput = & $gate
  Assert (($validOutput -join "`n").Contains('SECURITY_GATE_POLICY_PASS')) 'valid allowlist policy fixture must pass'

  $expiredAllowlist = Join-Path $contractArtifactDir 'allowlist-expired.json'
  @'
{
  "allowlist": [
    {
      "id": "GHSA-test-expired",
      "package": "test-package",
      "ecosystem": "npm",
      "severity": "high",
      "owner": "security",
      "reason": "contract fixture",
      "expiresOn": "2000-01-01"
    }
  ]
}
'@ | Out-File -FilePath $expiredAllowlist -Encoding utf8

  $env:SECURITY_ALLOWLIST_PATH = $expiredAllowlist
  $env:SECURITY_ARTIFACT_DIR = Join-Path $contractArtifactDir 'expired-artifacts'
  $expiredFailed = $false
  try {
    & $gate | Out-Null
  } catch {
    $expiredFailed = $true
  }
  Assert $expiredFailed 'expired allowlist policy fixture must fail'

  $outsideArtifactDir = Join-Path $repoRoot 'security-contract-outside-artifacts'
  if (Test-Path $outsideArtifactDir) {
    Remove-Item -LiteralPath $outsideArtifactDir -Recurse -Force
  }
  New-Item -ItemType Directory -Force -Path $outsideArtifactDir | Out-Null
  $sentinel = Join-Path $outsideArtifactDir 'sentinel.txt'
  'do-not-delete' | Out-File -FilePath $sentinel -Encoding utf8
  $env:SECURITY_ALLOWLIST_PATH = $validAllowlist
  $env:SECURITY_ARTIFACT_DIR = $outsideArtifactDir
  $outsideFailed = $false
  try {
    & $gate | Out-Null
  } catch {
    $outsideFailed = $true
  }
  Assert $outsideFailed 'security gate must reject SECURITY_ARTIFACT_DIR outside qa/artifacts'
  Assert (Test-Path $sentinel) 'security gate must not delete outside artifact sentinel'
} finally {
  $env:SECURITY_ALLOWLIST_PATH = $previousAllowlistPath
  $env:SECURITY_GATE_VALIDATE_POLICY_ONLY = $previousPolicyOnly
  $env:SECURITY_ARTIFACT_DIR = $previousArtifactDir
}

Write-Output 'SECURITY_GATE_CONTRACT_PASS'
