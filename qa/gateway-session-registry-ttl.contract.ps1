$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$runbookPath = Join-Path $repoRoot 'docs/runbooks/gateway-session-registry-ttl.md'
$redisProfilePath = Join-Path $repoRoot 'backend/boot/src/main/resources/application-redis.yml'
$envExamplePath = Join-Path $repoRoot '.env.example'
$registryPath = Join-Path $repoRoot 'backend/boot/src/main/java/com/example/discord/gateway/RedisGatewaySessionRegistry.java'
$testPath = Join-Path $repoRoot 'backend/boot/src/test/java/com/example/discord/gateway/RedisGatewaySessionRegistryTest.java'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $runbookPath) 'docs/runbooks/gateway-session-registry-ttl.md is missing'
Assert (Test-Path $redisProfilePath) 'application-redis.yml is missing'
Assert (Test-Path $envExamplePath) '.env.example is missing'
Assert (Test-Path $registryPath) 'RedisGatewaySessionRegistry.java is missing'
Assert (Test-Path $testPath) 'RedisGatewaySessionRegistryTest.java is missing'

$runbook = Get-Content -Path $runbookPath -Raw
$profile = Get-Content -Path $redisProfilePath -Raw
$envExample = Get-Content -Path $envExamplePath -Raw
$registry = Get-Content -Path $registryPath -Raw
$test = Get-Content -Path $testPath -Raw

$requiredRunbookSnippets = @(
  'gateway:sessions:<sessionId>',
  'DISCORD_GATEWAY_SESSION_TTL_SECONDS=86400',
  'save refreshes the session key TTL',
  'sessions() removes index members',
  'Do not store access tokens',
  'LiveKit JWTs',
  'signed URLs'
)

foreach ($snippet in $requiredRunbookSnippets) {
  Assert ($runbook.Contains($snippet)) "Gateway session registry TTL runbook is missing required snippet: $snippet"
}

$requiredProfileSnippets = @(
  'session-ttl-seconds',
  '${DISCORD_GATEWAY_SESSION_TTL_SECONDS:86400}'
)

foreach ($snippet in $requiredProfileSnippets) {
  Assert ($profile.Contains($snippet)) "application-redis.yml is missing required session TTL snippet: $snippet"
}

Assert ($envExample.Contains('DISCORD_GATEWAY_SESSION_TTL_SECONDS=86400')) '.env.example is missing Gateway session TTL env'
Assert ($registry.Contains('opsForValue().set')) 'RedisGatewaySessionRegistry must store sessions as per-session keys'
Assert ($registry.Contains('opsForSet().add')) 'RedisGatewaySessionRegistry must maintain a session index set'
Assert ($registry.Contains('redis.expire')) 'RedisGatewaySessionRegistry must refresh TTLs'
Assert ($registry.Contains('redis.delete')) 'RedisGatewaySessionRegistry must delete malformed or stale session keys'
Assert ($test.Contains('sessionsPrunesIndexEntriesWhoseSessionKeyExpired')) 'TTL cleanup test is missing'
Assert ($test.Contains('sessionsPrunesMalformedSessionMetadata')) 'Malformed session cleanup test is missing'

Write-Output 'GATEWAY_SESSION_REGISTRY_TTL_CONTRACT_PASS'
