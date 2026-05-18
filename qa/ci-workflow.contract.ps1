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
  'qa-runtime:',
  'qa-toolchain:',
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
  'POSTGRES_JDBC_URL: jdbc:postgresql://127.0.0.1:5432/discord',
  'npx playwright install --with-deps chromium',
  'pwsh qa/real-backend-e2e.contract.ps1',
  'Start backend for runtime QA',
  './gradlew :backend:boot:bootRun',
  'pwsh qa/real-backend-e2e.ps1 -SkipServiceStart',
  'Stop runtime backend',
  'pwsh qa/toolchain-warning-scan.ps1',
  'actions/upload-artifact@v4',
  'qa/artifacts/real-backend-e2e',
  'qa/artifacts/toolchain',
  './gradlew test',
  'npm ci',
  'npm run openapi:check',
  'npm test --workspaces',
  'npm run build --workspace @discord-clone/web'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "CI workflow is missing required snippet: $snippet"
}

$postgresServiceCount = ([regex]::Matches($content, 'postgres:\s*\r?\n\s*image: postgres:17')).Count
Assert ($postgresServiceCount -ge 3) "CI workflow should provision PostgreSQL for backend, qa-runtime, and qa-toolchain jobs"

Write-Output 'CI_WORKFLOW_CONTRACT_PASS'
