$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$workflowPath = Join-Path $repoRoot '.github/workflows/ci.yml'
$redisTestPath = Join-Path $repoRoot 'apps/web/tests/components/csp-report-rate-limiter.redis.test.ts'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $workflowPath) '.github/workflows/ci.yml is missing'
Assert (Test-Path $redisTestPath) 'Docker-backed CSP Redis limiter test is missing'

$workflowContent = Get-Content -Path $workflowPath -Raw
$testContent = Get-Content -Path $redisTestPath -Raw

Assert ($testContent.Contains("process.env.NUXT_RUN_DOCKER_TESTS === 'true'")) 'Redis limiter integration test must stay env-gated'
Assert ($testContent.Contains("redis:7-alpine")) 'Redis limiter integration test must use real Redis through Docker'
Assert ($testContent.Contains("127.0.0.1::6379")) 'Redis limiter integration test must bind Redis to loopback only'

$requiredWorkflowSnippets = @(
  'qa-web-docker:',
  'Check Docker availability',
  'docker version',
  'Web Docker integration tests',
  'NUXT_RUN_DOCKER_TESTS: ''true''',
  'npm test --workspace @discord-clone/web -- csp-report-rate-limiter.redis.test.ts'
)

foreach ($snippet in $requiredWorkflowSnippets) {
  Assert ($workflowContent.Contains($snippet)) "CI workflow is missing Docker web test snippet: $snippet"
}

Write-Output 'WEB_DOCKER_TESTS_CONTRACT_PASS'
