$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$runbookPath = Join-Path $repoRoot 'docs/runbooks/kafka-gateway-dlq-runbook.md'
$kafkaProfilePath = Join-Path $repoRoot 'backend/boot/src/main/resources/application-kafka.yml'
$envExamplePath = Join-Path $repoRoot '.env.example'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $runbookPath) 'docs/runbooks/kafka-gateway-dlq-runbook.md is missing'
Assert (Test-Path $kafkaProfilePath) 'application-kafka.yml is missing'
Assert (Test-Path $envExamplePath) '.env.example is missing'

$runbook = Get-Content -Path $runbookPath -Raw
$profile = Get-Content -Path $kafkaProfilePath -Raw
$envExample = Get-Content -Path $envExamplePath -Raw

$requiredRunbookSnippets = @(
  'discord.gateway.events.dead-letter',
  'MALFORMED_MESSAGE',
  'INVALID_ENVELOPE',
  'LISTENER_FAILURE',
  'Retention',
  '168 hours',
  'Alert threshold',
  'page when the DLQ count is greater than 0',
  'Replay',
  'Discard',
  'SECURITY_ADMIN',
  'change ticket',
  'Do not copy raw Kafka payloads',
  'messageSha256Prefix',
  'discord.gateway.kafka.dlq.records',
  'kafka-console-consumer',
  'kafka-console-producer'
)

foreach ($snippet in $requiredRunbookSnippets) {
  Assert ($runbook.Contains($snippet)) "Kafka Gateway DLQ runbook is missing required snippet: $snippet"
}

$forbiddenRunbookSnippets = @(
  'accessToken":',
  'signedUrl":',
  'paste the payload'
)

foreach ($snippet in $forbiddenRunbookSnippets) {
  Assert (-not $runbook.Contains($snippet)) "Kafka Gateway DLQ runbook contains unsafe snippet: $snippet"
}

$requiredProfileSnippets = @(
  'gateway-dlq-retention-hours',
  '${DISCORD_KAFKA_GATEWAY_DLQ_RETENTION_HOURS:168}',
  'gateway-dlq-alert-threshold',
  '${DISCORD_KAFKA_GATEWAY_DLQ_ALERT_THRESHOLD:1}'
)

foreach ($snippet in $requiredProfileSnippets) {
  Assert ($profile.Contains($snippet)) "application-kafka.yml is missing required DLQ policy snippet: $snippet"
}

$requiredEnvSnippets = @(
  'DISCORD_KAFKA_GATEWAY_DLQ_RETENTION_HOURS=168',
  'DISCORD_KAFKA_GATEWAY_DLQ_ALERT_THRESHOLD=1'
)

foreach ($snippet in $requiredEnvSnippets) {
  Assert ($envExample.Contains($snippet)) ".env.example is missing required DLQ policy snippet: $snippet"
}

Write-Output 'KAFKA_GATEWAY_DLQ_RUNBOOK_CONTRACT_PASS'
