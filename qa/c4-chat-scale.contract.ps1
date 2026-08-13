$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$routingDir = Join-Path $repoRoot 'qa/c4-chat-scale'
$policyPath = Join-Path $routingDir 'routing-policy.ps1'
$testsPath = Join-Path $routingDir 'routing-policy.tests.ps1'

function Assert($condition, $message) {
    if (-not $condition) {
        throw $message
    }
}

Assert (Test-Path $policyPath) 'routing-policy.ps1 is missing'
Assert (Test-Path $testsPath) 'routing-policy.tests.ps1 is missing'

foreach ($path in @($policyPath, $testsPath, $PSCommandPath)) {
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($path, [ref] $tokens, [ref] $errors) | Out-Null
    Assert ($errors.Count -eq 0) "PowerShell parse failed: $path"
}

$policy = Get-Content -Path $policyPath -Raw
$tests = Get-Content -Path $testsPath -Raw

Assert ($policy -match '(?m)^function\s+Get-ShardId\b') 'Get-ShardId function is missing'
Assert ($policy.Contains('[ArgumentException]')) 'ArgumentException validation is missing'
Assert ($policy.Contains('SHA256')) 'SHA-256 hashing is missing'
Assert ($policy.Contains('ToUInt32')) 'little-endian hash extraction is missing'
Assert ($tests.Contains('C4_ROUTING_TEST_PASS')) 'routing test pass marker is missing'
Assert ($tests.Contains('RED') -or $tests.Contains('routing-policy.ps1')) 'RED test contract is missing'

$testOutput = & pwsh -NoProfile -File $testsPath 2>&1
Assert ($LASTEXITCODE -eq 0) "Routing behavior test failed: $($testOutput -join [Environment]::NewLine)"
Assert (($testOutput -join [Environment]::NewLine).Contains('C4_ROUTING_TEST_PASS')) 'Routing behavior test did not report pass marker'

Write-Output 'C4_CHAT_SCALE_CONTRACT_PASS'
