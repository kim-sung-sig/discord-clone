$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$health = Join-Path $repoRoot 'qa/central-compose-health.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $health) 'central compose health script is missing'

$healthText = Get-Content -Path $health -Raw
Assert ($healthText.Contains('postgres-source')) 'health script must check central postgres-source'
Assert ($healthText.Contains('ms-redis')) 'health script must check central ms-redis'
Assert ($healthText.Contains('ms-kafka')) 'health script must check central ms-kafka'
Assert ($healthText.Contains('pg_isready')) 'health script must verify Postgres readiness'
Assert ($healthText.Contains('redis-cli')) 'health script must verify Redis readiness'
Assert ($healthText.Contains('REDISCLI_AUTH')) 'health script must pass Redis CLI password through REDISCLI_AUTH'
Assert (-not $healthText.Contains('redis-cli -a')) 'health script must not pass Redis password through redis-cli -a'
Assert ($healthText.Contains('29092')) 'health script must verify Kafka broker port readiness'
Assert ($healthText.Contains('Write-HealthDiagnostics')) 'health script must print diagnostics on readiness failures'
Assert ($healthText.Contains('docker ps -a')) 'health script must include Docker container state in diagnostics'
Assert ($healthText.Contains('docker compose -f $compose ps')) 'health script must include Compose service state in diagnostics'
Assert ($healthText.Contains('Get-NetTCPConnection')) 'health script must include Windows port owner diagnostics'
Assert ($healthText.Contains('ss -ltnp')) 'health script must include Linux port owner diagnostics'
Assert ($healthText.Contains('CENTRAL_COMPOSE_HEALTH_PASS')) 'health script must emit a stable pass marker'
Assert ($healthText.Contains('CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE')) 'health script must expose a controlled diagnostic smoke mode'

Write-Output 'CENTRAL_COMPOSE_HEALTH_CONTRACT_PASS'
