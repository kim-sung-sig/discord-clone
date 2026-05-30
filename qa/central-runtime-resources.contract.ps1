$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$postgresProfile = Join-Path $repoRoot 'backend/boot/src/main/resources/application-postgres.yml'
$redisProfile = Join-Path $repoRoot 'backend/boot/src/main/resources/application-redis.yml'
$kafkaProfile = Join-Path $repoRoot 'backend/boot/src/main/resources/application-kafka.yml'
$envExample = Join-Path $repoRoot '.env.example'
$compose = Join-Path $repoRoot 'infra/docker/docker-compose.yml'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $postgresProfile) 'application-postgres.yml is missing'
Assert (Test-Path $redisProfile) 'application-redis.yml is missing'
Assert (Test-Path $kafkaProfile) 'application-kafka.yml is missing'
Assert (Test-Path $envExample) '.env.example is missing'
Assert (Test-Path $compose) 'infra docker-compose.yml is missing'

$postgres = Get-Content -Path $postgresProfile -Raw
$redis = Get-Content -Path $redisProfile -Raw
$kafka = Get-Content -Path $kafkaProfile -Raw
$env = Get-Content -Path $envExample -Raw
$composeText = Get-Content -Path $compose -Raw

Assert ($postgres.Contains('jdbc:postgresql://127.0.0.1:15432/discord')) 'postgres profile must default to postgres-source on 15432'
Assert ($postgres.Contains('username: ${POSTGRES_USER:dev_user}')) 'postgres profile must default to dev_user'
Assert ($postgres.Contains('password: ${POSTGRES_PASSWORD:dev_password}')) 'postgres profile must default to dev_password'
Assert ($postgres.Contains('flyway:')) 'postgres profile must declare flyway settings in resources'
Assert ($postgres.Contains('locations: ${SPRING_FLYWAY_LOCATIONS:classpath:db/migration}')) 'postgres profile must expose flyway locations'
Assert ($postgres.Contains('baseline-on-migrate: ${SPRING_FLYWAY_BASELINE_ON_MIGRATE:true}')) 'postgres profile must expose flyway baseline-on-migrate'

Assert ($redis.Contains('port: ${SPRING_DATA_REDIS_PORT:16379}')) 'redis profile must default to ms-redis host port 16379'
Assert ($redis.Contains('password: ${SPRING_DATA_REDIS_PASSWORD:dev_password}')) 'redis profile must default to ms-redis password'
Assert ($kafka.Contains('bootstrap-servers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:127.0.0.1:29092}')) 'kafka profile must default to ms-kafka host port 29092'

Assert ($env.Contains('SPRING_PROFILES_ACTIVE=postgres,redis,kafka')) '.env.example must prefer central runtime profiles'
Assert ($env.Contains('SPRING_DATA_REDIS_PORT=16379')) '.env.example must point Redis to host port 16379'
Assert ($env.Contains('SPRING_DATA_REDIS_PASSWORD=dev_password')) '.env.example must include Redis password'
Assert ($env.Contains('SPRING_KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:29092')) '.env.example must include Kafka bootstrap servers'
Assert ($env.Contains('NUXT_CSP_REPORT_RATE_LIMIT_REDIS_URL=redis://:dev_password@127.0.0.1:16379')) '.env.example must route CSP rate limiting to authenticated Redis by default'

Assert ($composeText.Contains('postgres-source:')) 'docker compose must expose postgres-source service'
Assert ($composeText.Contains('"15432:5432"')) 'docker compose must map postgres-source to host port 15432'
Assert ($composeText.Contains('ms-redis:')) 'docker compose must expose ms-redis service'
Assert ($composeText.Contains('"16379:6379"')) 'docker compose must map ms-redis to host port 16379'
Assert ($composeText.Contains('--requirepass')) 'docker compose Redis must require the shared local password'
Assert ($composeText.Contains('REDISCLI_AUTH=')) 'docker compose Redis healthcheck must pass password through REDISCLI_AUTH'
Assert (-not $composeText.Contains('redis-cli -a')) 'docker compose Redis healthcheck must not pass password through redis-cli -a'
Assert ($composeText.Contains('ms-kafka:')) 'docker compose must expose ms-kafka service'
Assert ($composeText.Contains('"29092:29092"')) 'docker compose must map ms-kafka to host port 29092'

Write-Output 'CENTRAL_RUNTIME_RESOURCES_CONTRACT_PASS'
