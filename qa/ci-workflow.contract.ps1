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
  'qa-security:',
  'qa-toolchain:',
  'runs-on: ubuntu-latest',
  'timeout-minutes: 20',
  'permissions:',
  'contents: read',
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
  'pwsh qa/security-gate.contract.ps1',
  'pwsh qa/security-gate.ps1',
  'security-gate-artifacts',
  'qa/artifacts/security',
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

$qaScriptMatches = [regex]::Matches($content, 'qa/[A-Za-z0-9_.-]+\.ps1')
foreach ($match in $qaScriptMatches) {
  $relativePath = $match.Value
  $repoPath = Join-Path $repoRoot ($relativePath -replace '/', [IO.Path]::DirectorySeparatorChar)
  Assert (Test-Path $repoPath) "CI workflow references missing file: $relativePath"

  $tracked = & git -c 'safe.directory=*' -C $repoRoot ls-files -- $relativePath
  Assert (-not [string]::IsNullOrWhiteSpace($tracked)) "CI workflow references untracked file: $relativePath"
}

$postgresServiceCount = ([regex]::Matches($content, 'postgres:\s*\r?\n\s*image: postgres:17')).Count
Assert ($postgresServiceCount -ge 3) "CI workflow should provision PostgreSQL for backend, qa-runtime, and qa-toolchain jobs"

Write-Output 'CI_WORKFLOW_CONTRACT_PASS'
