$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$runbookPath = Join-Path $repoRoot 'docs/runbooks/redis-gateway-dlq-runbook.md'
$redisProfilePath = Join-Path $repoRoot 'backend/boot/src/main/resources/application-redis.yml'
$envExamplePath = Join-Path $repoRoot '.env.example'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $runbookPath) 'docs/runbooks/redis-gateway-dlq-runbook.md is missing'
Assert (Test-Path $redisProfilePath) 'application-redis.yml is missing'
Assert (Test-Path $envExamplePath) '.env.example is missing'

$runbook = Get-Content -Path $runbookPath -Raw
$profile = Get-Content -Path $redisProfilePath -Raw
$envExample = Get-Content -Path $envExamplePath -Raw

$requiredRunbookSnippets = @(
  'gateway:dead-letter',
  'DISCORD_GATEWAY_REDIS_STREAM_MAX_LENGTH=10000',
  'MALFORMED_RECORD',
  'LISTENER_FAILURE',
  'Alert threshold',
  'page when the DLQ count is greater than 0',
  'Replay',
  'Discard',
  'SECURITY_ADMIN',
  'change ticket',
  'Do not copy raw Redis stream payloads',
  'messageSha256Prefix',
  'redis-cli XREVRANGE',
  'REDISCLI_AUTH'
)

foreach ($snippet in $requiredRunbookSnippets) {
  Assert ($runbook.Contains($snippet)) "Redis Gateway DLQ runbook is missing required snippet: $snippet"
}

$forbiddenRunbookSnippets = @(
  'accessToken":',
  'signedUrl":',
  'paste the payload',
  'redis-cli -a'
)

foreach ($snippet in $forbiddenRunbookSnippets) {
  Assert (-not $runbook.Contains($snippet)) "Redis Gateway DLQ runbook contains unsafe snippet: $snippet"
}

$requiredProfileSnippets = @(
  'redis-dlq-stream',
  '${DISCORD_GATEWAY_REDIS_DLQ_STREAM:gateway:dead-letter}',
  'redis-dlq-alert-threshold',
  '${DISCORD_GATEWAY_REDIS_DLQ_ALERT_THRESHOLD:1}'
)

foreach ($snippet in $requiredProfileSnippets) {
  Assert ($profile.Contains($snippet)) "application-redis.yml is missing required DLQ policy snippet: $snippet"
}

$requiredEnvSnippets = @(
  'DISCORD_GATEWAY_REDIS_DLQ_STREAM=gateway:dead-letter',
  'DISCORD_GATEWAY_REDIS_DLQ_ALERT_THRESHOLD=1'
)

foreach ($snippet in $requiredEnvSnippets) {
  Assert ($envExample.Contains($snippet)) ".env.example is missing required DLQ policy snippet: $snippet"
}

Write-Output 'REDIS_GATEWAY_DLQ_RUNBOOK_CONTRACT_PASS'
