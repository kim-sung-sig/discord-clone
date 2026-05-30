$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$smoke = Join-Path $repoRoot 'qa/central-kafka-gateway-smoke.ps1'
$backendTest = Join-Path $repoRoot 'backend/boot/src/test/java/com/example/discord/gateway/CentralKafkaGatewayEventBusSmokeTest.java'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $smoke) 'central kafka gateway smoke script is missing'
Assert (Test-Path $backendTest) 'central kafka gateway smoke test is missing'

$smokeText = Get-Content -Path $smoke -Raw
Assert ($smokeText.Contains('ms-kafka')) 'smoke script must use the central ms-kafka compose service'
Assert ($smokeText.Contains('DISCORD_RUN_CENTRAL_KAFKA_GATEWAY_SMOKE')) 'smoke script must enable backend central Kafka Gateway smoke'
Assert ($smokeText.Contains('CentralKafkaGatewayEventBusSmokeTest')) 'smoke script must run the central Kafka Gateway smoke test'

$testText = Get-Content -Path $backendTest -Raw
Assert ($testText.Contains('KafkaGatewayEventBus')) 'smoke test must exercise KafkaGatewayEventBus'
Assert ($testText.Contains('KafkaConsumer')) 'smoke test must consume through the real Kafka broker'
Assert ($testText.Contains('addEventListener')) 'smoke test must prove a second node receives the event'

Write-Output 'CENTRAL_KAFKA_GATEWAY_SMOKE_CONTRACT_PASS'
