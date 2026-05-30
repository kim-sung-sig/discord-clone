$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $repoRoot 'infra/docker/docker-compose.yml'
$postgresUser = $env:POSTGRES_USER
$postgresDb = $env:POSTGRES_DB
$redisPassword = $env:SPRING_DATA_REDIS_PASSWORD
if ([string]::IsNullOrWhiteSpace($postgresUser)) {
  $postgresUser = 'dev_user'
}
if ([string]::IsNullOrWhiteSpace($postgresDb)) {
  $postgresDb = 'discord'
}
if ([string]::IsNullOrWhiteSpace($redisPassword)) {
  $redisPassword = 'dev_password'
}

function Test-TcpPort($hostName, $port) {
  $client = [System.Net.Sockets.TcpClient]::new()
  try {
    $connect = $client.ConnectAsync($hostName, $port)
    return $connect.Wait(1000) -and $client.Connected
  } catch {
    return $false
  } finally {
    $client.Dispose()
  }
}

function Test-ExistingPostgres {
  try {
    $containerId = docker ps --filter 'name=^/postgres-source$' --format '{{.ID}}'
    if ([string]::IsNullOrWhiteSpace($containerId)) {
      return $false
    }
    docker exec postgres-source pg_isready -U $postgresUser -d $postgresDb | Out-Null
    return $true
  } catch {
    return $false
  }
}

function Test-ExistingRedis {
  try {
    $containerId = docker ps --filter 'name=^/ms-redis$' --format '{{.ID}}'
    if ([string]::IsNullOrWhiteSpace($containerId)) {
      return $false
    }
    $pong = docker exec -e "REDISCLI_AUTH=$redisPassword" ms-redis redis-cli ping
    return $pong.Trim() -eq 'PONG'
  } catch {
    return $false
  }
}

function Test-ExistingKafka {
  try {
    $containerId = docker ps --filter 'name=^/ms-kafka$' --format '{{.ID}}'
    return -not [string]::IsNullOrWhiteSpace($containerId) -and (Test-TcpPort '127.0.0.1' 29092)
  } catch {
    return $false
  }
}

function Write-HealthDiagnostics($resourceName, $port) {
  Write-Output "CENTRAL_COMPOSE_HEALTH_DIAGNOSTICS resource=$resourceName port=$port"
  try {
    docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | Write-Output
  } catch {
    Write-Output "docker ps diagnostics failed: $($_.Exception.Message)"
  }
  try {
    docker compose -f $compose ps | Write-Output
  } catch {
    Write-Output "docker compose diagnostics failed: $($_.Exception.Message)"
  }
  try {
    $isWindows = $PSVersionTable.Platform -eq 'Win32NT' -or $env:OS -eq 'Windows_NT'
    if ($isWindows) {
      Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
        Select-Object LocalAddress, LocalPort, State, OwningProcess |
        Format-Table -AutoSize |
        Out-String |
        Write-Output
    } else {
      ss -ltnp 2>&1 | Select-String ":$port" | Write-Output
    }
  } catch {
    Write-Output "port diagnostics failed: $($_.Exception.Message)"
  }
}

function Wait-ComposePostgres {
  for ($attempt = 0; $attempt -lt 60; $attempt += 1) {
    try {
      docker compose -f $compose exec -T postgres-source pg_isready -U $postgresUser -d $postgresDb | Out-Null
      return $true
    } catch {
      Start-Sleep -Seconds 1
    }
  }
  return $false
}

function Wait-ComposeRedis {
  for ($attempt = 0; $attempt -lt 60; $attempt += 1) {
    try {
      $pong = docker compose -f $compose exec -T -e "REDISCLI_AUTH=$redisPassword" ms-redis redis-cli ping
      if ($pong.Trim() -eq 'PONG') {
        return $true
      }
    } catch {
      Start-Sleep -Seconds 1
    }
  }
  return $false
}

function Wait-KafkaPort {
  for ($attempt = 0; $attempt -lt 60; $attempt += 1) {
    if (Test-TcpPort '127.0.0.1' 29092) {
      return $true
    }
    Start-Sleep -Seconds 1
  }
  return $false
}

Push-Location $repoRoot
try {
  if ($env:CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE -eq 'true') {
    Write-HealthDiagnostics 'diagnostic-smoke' 1
    throw 'CENTRAL_COMPOSE_HEALTH_DIAGNOSTIC_SMOKE_FAILURE'
  }

  if (-not (Test-ExistingPostgres)) {
    try {
      docker compose -f $compose up -d postgres-source | Write-Output
    } catch {
      Write-HealthDiagnostics 'postgres-source' 15432
      throw
    }
    if (-not (Wait-ComposePostgres)) {
      Write-HealthDiagnostics 'postgres-source' 15432
      throw 'central postgres-source did not become ready'
    }
  }

  if (-not (Test-ExistingRedis)) {
    try {
      docker compose -f $compose up -d ms-redis | Write-Output
    } catch {
      Write-HealthDiagnostics 'ms-redis' 16379
      throw
    }
    if (-not (Wait-ComposeRedis)) {
      Write-HealthDiagnostics 'ms-redis' 16379
      throw 'central ms-redis did not become ready'
    }
  }

  if (-not (Test-ExistingKafka)) {
    try {
      docker compose -f $compose up -d ms-kafka | Write-Output
    } catch {
      Write-HealthDiagnostics 'ms-kafka' 29092
      throw
    }
    if (-not (Wait-KafkaPort)) {
      Write-HealthDiagnostics 'ms-kafka' 29092
      throw 'central ms-kafka did not become ready'
    }
  }

  Write-Output 'CENTRAL_COMPOSE_HEALTH_PASS'
} finally {
  Pop-Location
}
