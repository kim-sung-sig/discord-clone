$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$workflowPath = Join-Path $repoRoot '.github/workflows/ci.yml'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $workflowPath) '.github/workflows/ci.yml is missing'

$content = Get-Content -Path $workflowPath -Raw
$requiredSnippets = @(
  'name: ci',
  'pull_request:',
  'push:',
  'backend:',
  'frontend:',
  'qa-web-docker:',
  'qa-dashboard-guard-health:',
  'qa-runtime:',
  'qa-toolchain:',
  'qa-production-profile-guard:',
  'qa-admin-cli:',
  'qa-central-redis:',
  'qa-central-kafka:',
  'qa-security:',
  'runs-on: ubuntu-latest',
  'actions/setup-java@v4',
  'java-version: ''21''',
  'actions/setup-node@v4',
  'node-version: ''22''',
  'postgres:',
  'services:',
  'POSTGRES_DB: discord',
  'POSTGRES_USER: dev_user',
  'POSTGRES_PASSWORD: dev_password',
  'MANAGEMENT_HEALTH_REDIS_ENABLED: ''false''',
  'POSTGRES_JDBC_URL: jdbc:postgresql://127.0.0.1:5432/discord',
  'npx playwright install --with-deps chromium',
  'pwsh qa/real-backend-browser-smoke-default.contract.ps1',
  'pwsh qa/real-backend-e2e.contract.ps1',
  'Start backend for runtime QA',
  './gradlew :backend:boot:bootRun',
  'pwsh qa/real-backend-e2e.ps1 -BackendUrl http://127.0.0.1:8080 -SkipServiceStart',
  'Stop runtime backend',
  'pwsh qa/toolchain-warning-scan.ps1',
  'pwsh qa/production-profile-guard-smoke.contract.ps1',
  'pwsh qa/production-profile-guard-smoke.ps1',
  'production-profile-guard-artifacts',
  'qa/artifacts/production-profile-guard',
  'pwsh qa/admin-cli-bootrun-smoke.contract.ps1',
  'pwsh qa/admin-cli-bootrun-smoke.ps1',
  'ADMIN_CLI_ARTIFACT_DIR: qa/artifacts/admin-cli/ci',
  'admin-cli-artifacts',
  'qa/artifacts/admin-cli',
  'pwsh qa/central-redis-smoke.contract.ps1',
  'pwsh qa/central-compose-health.contract.ps1',
  'pwsh qa/central-compose-health-diagnostics-smoke.contract.ps1',
  'pwsh qa/central-redis-smoke.ps1',
  'pwsh qa/redis-gateway-dlq-runbook.contract.ps1',
  'pwsh qa/gateway-session-registry-ttl.contract.ps1',
  'pwsh qa/central-redis-ci-artifacts.ps1',
  'Collect central Redis failure artifacts',
  'Upload central Redis artifacts',
  'central-redis-artifacts',
  'qa/artifacts/central-redis',
  'CENTRAL_REDIS_ARTIFACT_DIR: qa/artifacts/central-redis/ci',
  'pwsh qa/central-kafka-gateway-smoke.contract.ps1',
  'pwsh qa/central-kafka-ci-artifacts.contract.ps1',
  'pwsh qa/kafka-gateway-dlq-runbook.contract.ps1',
  'pwsh qa/central-kafka-gateway-smoke.ps1',
  'pwsh qa/central-kafka-ci-artifacts.ps1',
  'Collect central Kafka failure artifacts',
  'Upload central Kafka artifacts',
  'central-kafka-artifacts',
  'qa/artifacts/central-kafka',
  'CENTRAL_KAFKA_ARTIFACT_DIR: qa/artifacts/central-kafka/ci',
  'SPRING_DATA_REDIS_PASSWORD: dev_password',
  'SPRING_KAFKA_BOOTSTRAP_SERVERS: 127.0.0.1:29092',
  'pwsh qa/security-gate.contract.ps1',
  'pwsh qa/security-gate.ps1',
  'security-gate-artifacts',
  'qa/artifacts/security',
  'actions/upload-artifact@v4',
  'qa/artifacts/real-backend-e2e',
  'qa/artifacts/toolchain',
  './gradlew test',
  'npm ci',
  'pwsh qa/livekit-media-smoke.contract.ps1',
  'npm run openapi:check',
  'npm test --workspaces',
  'npm run build --workspace @discord-clone/web',
  'pwsh qa/web-docker-tests.contract.ps1',
  'docker version',
  'NUXT_RUN_DOCKER_TESTS: ''true''',
  'npm test --workspace @discord-clone/web -- csp-report-rate-limiter.redis.test.ts',
  'pwsh qa/dashboard-guard-health-smoke.contract.ps1',
  'pwsh qa/dashboard-guard-health-smoke.ps1',
  'NUXT_SECURITY_DASHBOARD_TOKEN: ci-dashboard-guard-token',
  'DASHBOARD_GUARD_HEALTH_ARTIFACT_DIR: qa/artifacts/dashboard-guard-health/ci',
  'dashboard-guard-health-artifacts',
  'qa/artifacts/dashboard-guard-health'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "CI workflow is missing required snippet: $snippet"
}

$postgresServiceCount = ([regex]::Matches($content, 'postgres:\s*\r?\n\s*image: postgres:17')).Count
Assert ($postgresServiceCount -ge 3) "CI workflow should provision PostgreSQL for backend, qa-runtime, and qa-toolchain jobs"

Write-Output 'CI_WORKFLOW_CONTRACT_PASS'
