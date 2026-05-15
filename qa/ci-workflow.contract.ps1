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
  'POSTGRES_DB: discord',
  'POSTGRES_USER: dev_user',
  'POSTGRES_PASSWORD: dev_password',
  'npx playwright install --with-deps chromium',
  'pwsh qa/real-backend-e2e.contract.ps1',
  'pwsh qa/real-backend-e2e.ps1',
  'pwsh qa/toolchain-warning-scan.ps1',
  'actions/upload-artifact@v4',
  'qa/artifacts/real-backend-e2e',
  'qa/artifacts/toolchain',
  './gradlew test',
  'npm ci',
  'npm test --workspaces',
  'npm run build --workspace @discord-clone/web'
)

foreach ($snippet in $requiredSnippets) {
  Assert ($content.Contains($snippet)) "CI workflow is missing required snippet: $snippet"
}

Write-Output 'CI_WORKFLOW_CONTRACT_PASS'
