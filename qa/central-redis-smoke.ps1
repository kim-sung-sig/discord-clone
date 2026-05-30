$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $repoRoot 'infra/docker/docker-compose.yml'
$redisPassword = $env:SPRING_DATA_REDIS_PASSWORD
if ([string]::IsNullOrWhiteSpace($redisPassword)) {
  $redisPassword = 'dev_password'
}
$artifactDir = $env:CENTRAL_REDIS_ARTIFACT_DIR
if ([string]::IsNullOrWhiteSpace($artifactDir)) {
  $artifactDir = Join-Path $repoRoot 'qa/artifacts/central-redis/local'
}
if (-not [System.IO.Path]::IsPathRooted($artifactDir)) {
  $artifactDir = Join-Path $repoRoot $artifactDir
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

function Test-ExistingMsRedis($password) {
  try {
    $containerId = docker ps --filter 'name=^/ms-redis$' --format '{{.ID}}'
    if ([string]::IsNullOrWhiteSpace($containerId)) {
      return $false
    }
    $pong = docker exec -e "REDISCLI_AUTH=$password" ms-redis redis-cli ping
    return $pong.Trim() -eq 'PONG'
  } catch {
    return $false
  }
}

function Wait-ComposeRedis($composeFile, $password) {
  for ($attempt = 0; $attempt -lt 30; $attempt += 1) {
    try {
      $pong = docker compose -f $composeFile exec -T -e "REDISCLI_AUTH=$password" ms-redis redis-cli ping
      if ($pong.Trim() -eq 'PONG') {
        return $true
      }
    } catch {
      Start-Sleep -Seconds 1
    }
  }
  return $false
}

Push-Location $repoRoot
try {
  New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null
  $ready = Test-ExistingMsRedis $redisPassword
  if (-not $ready) {
    docker compose -f $compose up -d ms-redis | Write-Output
    $ready = Wait-ComposeRedis $compose $redisPassword
  }
  if (-not $ready) {
    throw 'central ms-redis did not become ready'
  }

  $env:SPRING_DATA_REDIS_HOST = '127.0.0.1'
  $env:SPRING_DATA_REDIS_PORT = '16379'
  $env:SPRING_DATA_REDIS_PASSWORD = $redisPassword
  $env:DISCORD_RUN_CENTRAL_REDIS_SMOKE = 'true'
  $env:DISCORD_RUN_CENTRAL_REDIS_GATEWAY_SMOKE = 'true'
  Invoke-Native (Get-GradleCommand) @(
    ':backend:boot:test',
    '--tests',
    'com.example.discord.ops.CentralRedisConnectivitySmokeTest',
    '--tests',
    'com.example.discord.gateway.CentralRedisGatewayFanoutSmokeTest',
    '--tests',
    'com.example.discord.gateway.CentralRedisGatewaySessionRegistrySmokeTest',
    '--rerun-tasks'
  )

  $env:NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL = "redis://:$redisPassword@127.0.0.1:16379"
  $env:NUXT_RUN_CENTRAL_REDIS_SMOKE = 'true'
  Invoke-Native 'npm' @(
    'test',
    '-w',
    'apps/web',
    '--',
    '--reporter=default',
    '--reporter=junit',
    "--outputFile.junit=$artifactDir/vitest-junit.xml",
    'csp-report-rate-limiter.central-redis.test.ts'
  )

  Write-Output 'CENTRAL_REDIS_SMOKE_PASS'
} finally {
  Pop-Location
}
