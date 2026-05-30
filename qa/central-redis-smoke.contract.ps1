$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$smoke = Join-Path $repoRoot 'qa/central-redis-smoke.ps1'
$backendTest = Join-Path $repoRoot 'backend/boot/src/test/java/com/example/discord/ops/CentralRedisConnectivitySmokeTest.java'
$gatewayTest = Join-Path $repoRoot 'backend/boot/src/test/java/com/example/discord/gateway/CentralRedisGatewayFanoutSmokeTest.java'
$gatewaySessionTest = Join-Path $repoRoot 'backend/boot/src/test/java/com/example/discord/gateway/CentralRedisGatewaySessionRegistrySmokeTest.java'
$webTest = Join-Path $repoRoot 'apps/web/tests/components/csp-report-rate-limiter.central-redis.test.ts'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $smoke) 'central redis smoke script is missing'
Assert (Test-Path $backendTest) 'backend central redis smoke test is missing'
Assert (Test-Path $gatewayTest) 'backend central Redis Gateway fanout smoke test is missing'
Assert (Test-Path $gatewaySessionTest) 'backend central Redis Gateway session registry smoke test is missing'
Assert (Test-Path $webTest) 'web central redis smoke test is missing'

$smokeText = Get-Content -Path $smoke -Raw
Assert ($smokeText.Contains('ms-redis')) 'smoke script must use the central ms-redis compose service'
Assert ($smokeText.Contains('DISCORD_RUN_CENTRAL_REDIS_SMOKE')) 'smoke script must enable backend central Redis smoke'
Assert ($smokeText.Contains('DISCORD_RUN_CENTRAL_REDIS_GATEWAY_SMOKE')) 'smoke script must enable backend central Redis Gateway fanout smoke'
Assert ($smokeText.Contains('NUXT_RUN_CENTRAL_REDIS_SMOKE')) 'smoke script must enable web central Redis smoke'
Assert ($smokeText.Contains('CentralRedisConnectivitySmokeTest')) 'smoke script must run backend central Redis smoke test'
Assert ($smokeText.Contains('CentralRedisGatewayFanoutSmokeTest')) 'smoke script must run backend central Redis Gateway fanout smoke test'
Assert ($smokeText.Contains('CentralRedisGatewaySessionRegistrySmokeTest')) 'smoke script must run backend central Redis Gateway session registry smoke test'
Assert ($smokeText.Contains('csp-report-rate-limiter.central-redis.test.ts')) 'smoke script must run web central Redis smoke test'
Assert ($smokeText.Contains('CENTRAL_REDIS_ARTIFACT_DIR')) 'smoke script must support CI artifact output directory'
Assert ($smokeText.Contains('--reporter=junit')) 'smoke script must emit a Vitest JUnit report'
Assert ($smokeText.Contains('vitest-junit.xml')) 'smoke script must write a stable Vitest report file'
Assert ($smokeText.Contains('REDISCLI_AUTH')) 'smoke script must pass Redis CLI password through REDISCLI_AUTH'
Assert (-not $smokeText.Contains('redis-cli -a')) 'smoke script must not pass Redis password through redis-cli -a'

$gatewayText = Get-Content -Path $gatewayTest -Raw
Assert ($gatewayText.Contains('DISCORD_RUN_CENTRAL_REDIS_GATEWAY_SMOKE')) 'Gateway fanout smoke must be environment gated'
Assert ($gatewayText.Contains('twoGatewayNodesBothReceiveSameStreamEventThroughCentralRedis')) 'Gateway fanout smoke must prove two-node broadcast delivery'
Assert ($gatewayText.Contains('gatewayServicesDeliverOnlyVisibleChannelEventsThroughCentralRedis')) 'Gateway fanout smoke must prove service-level hidden channel filtering'
Assert ($gatewayText.Contains('discord-gateway-smoke')) 'Gateway fanout smoke must use an isolated consumer group prefix'

Write-Output 'CENTRAL_REDIS_SMOKE_CONTRACT_PASS'
