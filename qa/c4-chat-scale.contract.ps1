$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$routingDir = Join-Path $repoRoot 'qa/c4-chat-scale'
$policyPath = Join-Path $routingDir 'routing-policy.ps1'
$testsPath = Join-Path $routingDir 'routing-policy.tests.ps1'
$composePath = Join-Path $routingDir 'docker-compose.yml'
$schemaPath = Join-Path $routingDir 'init/001-schema.sql'
$pgbenchDir = Join-Path $routingDir 'pgbench'
$writePath = Join-Path $pgbenchDir 'write.sql'
$historyPath = Join-Path $pgbenchDir 'history.sql'
$searchPath = Join-Path $pgbenchDir 'search.sql'

function Assert($condition, $message) {
    if (-not $condition) {
        throw $message
    }
}

Assert (Test-Path $policyPath) 'routing-policy.ps1 is missing'
Assert (Test-Path $testsPath) 'routing-policy.tests.ps1 is missing'
Assert (Test-Path $composePath) 'docker-compose.yml is missing'
Assert (Test-Path $schemaPath) 'init/001-schema.sql is missing'
foreach ($path in @($writePath, $historyPath, $searchPath)) {
    Assert (Test-Path $path) "pgbench script is missing: $path"
}

foreach ($path in @($policyPath, $testsPath, $PSCommandPath)) {
    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($path, [ref] $tokens, [ref] $errors) | Out-Null
    Assert ($errors.Count -eq 0) "PowerShell parse failed: $path"
}

$policy = Get-Content -Path $policyPath -Raw
$tests = Get-Content -Path $testsPath -Raw
$compose = Get-Content -Path $composePath -Raw
$schema = Get-Content -Path $schemaPath -Raw
$write = Get-Content -Path $writePath -Raw
$history = Get-Content -Path $historyPath -Raw
$search = Get-Content -Path $searchPath -Raw

Assert ($policy -match '(?m)^function\s+Get-ShardId\b') 'Get-ShardId function is missing'
Assert ($policy.Contains('[ArgumentException]')) 'ArgumentException validation is missing'
Assert ($policy.Contains('SHA256')) 'SHA-256 hashing is missing'
Assert ($policy.Contains('ToUInt32')) 'little-endian hash extraction is missing'
Assert ($tests.Contains('C4_ROUTING_TEST_PASS')) 'routing test pass marker is missing'
Assert ($tests.Contains('RED') -or $tests.Contains('routing-policy.ps1')) 'RED test contract is missing'

foreach ($snippet in @('primary:', 'replica:', 'pgbench:', 'postgres:16-alpine', '15442:5432', '15443:5432', 'wal_level=replica', 'primary_conninfo', 'pg_wal_replay_pause', 'profiles:', 'load')) {
    Assert ($compose.Contains($snippet)) "compose required snippet is missing: $snippet"
}
foreach ($identifier in @('messages_template', 'messages_baseline', 'messages_date_range', 'messages_date_hash', 'PARTITION BY RANGE', 'PARTITION BY HASH', 'UNIQUE (chat_room_id, sequence)', 'event_date', 'chat_room_id', '0..29', '0..15', 'MODULUS 16')) {
    Assert ($schema.Contains($identifier)) "schema required identifier is missing: $identifier"
}
Assert ($write -match '(?i)ON\s+CONFLICT\s*\(\s*chat_room_id\s*,\s*sequence\s*\)\s*DO\s+NOTHING') 'write SQL must use idempotent ON CONFLICT DO NOTHING'
Assert ($history -match '(?i)sequence\s*>\s*:cursor') 'history SQL must use ordered sequence cursor'
Assert ($history -match '(?i)chat_room_id') 'history SQL must constrain chat room'
Assert ($search -match '(?i)chat_room_id') 'search SQL must constrain chat room'
Assert ($search -match '(?i)event_date') 'search SQL must constrain event date'

$testOutput = & pwsh -NoProfile -File $testsPath 2>&1
Assert ($LASTEXITCODE -eq 0) "Routing behavior test failed: $($testOutput -join [Environment]::NewLine)"
Assert (($testOutput -join [Environment]::NewLine).Contains('C4_ROUTING_TEST_PASS')) 'Routing behavior test did not report pass marker'

Write-Output 'C4_CHAT_SCALE_CONTRACT_PASS'
