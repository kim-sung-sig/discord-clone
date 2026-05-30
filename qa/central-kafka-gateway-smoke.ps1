$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $repoRoot 'infra/docker/docker-compose.yml'
$bootstrapServers = $env:SPRING_KAFKA_BOOTSTRAP_SERVERS
if ([string]::IsNullOrWhiteSpace($bootstrapServers)) {
  $bootstrapServers = '127.0.0.1:29092'
}

function Get-GradleCommand {
  $isWindows = $PSVersionTable.Platform -eq 'Win32NT' -or $env:OS -eq 'Windows_NT'
  if ($isWindows) {
    return Join-Path $repoRoot 'gradlew.bat'
  }
  return Join-Path $repoRoot 'gradlew'
}

function Invoke-Native($file, [string[]] $arguments) {
  & $file @arguments
  if ($LASTEXITCODE -ne 0) {
    throw "$file failed with exit code $LASTEXITCODE"
  }
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

function Test-ExistingMsKafka {
  try {
    $containerId = docker ps --filter 'name=^/ms-kafka$' --format '{{.ID}}'
    return -not [string]::IsNullOrWhiteSpace($containerId) -and (Test-TcpPort '127.0.0.1' 29092)
  } catch {
    return $false
  }
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
  $ready = Test-ExistingMsKafka
  if (-not $ready) {
    docker compose -f $compose up -d ms-kafka | Write-Output
    $ready = Wait-KafkaPort
  }
  if (-not $ready) {
    throw 'central ms-kafka did not become ready'
  }

  $env:SPRING_KAFKA_BOOTSTRAP_SERVERS = $bootstrapServers
  $env:DISCORD_RUN_CENTRAL_KAFKA_GATEWAY_SMOKE = 'true'
  Invoke-Native (Get-GradleCommand) @(
    ':backend:boot:test',
    '--tests',
    'com.example.discord.gateway.CentralKafkaGatewayEventBusSmokeTest',
    '--rerun-tasks'
  )

  Write-Output 'CENTRAL_KAFKA_GATEWAY_SMOKE_PASS'
} finally {
  Pop-Location
}
