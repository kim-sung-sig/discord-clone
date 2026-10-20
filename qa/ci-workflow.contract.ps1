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
  'pwsh qa/real-backend-e2e.ps1 -BackendUrl "$BACKEND_URL" -SkipServiceStart',
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

$runtimeBlock = [regex]::Match($content, '(?ms)^  qa-runtime:\r?\n(?<body>.*?)^  qa-security:').Groups['body'].Value
Assert (-not [string]::IsNullOrWhiteSpace($runtimeBlock)) 'CI workflow is missing the qa-runtime job block'
Assert ($runtimeBlock.Contains('id: start-backend')) 'qa-runtime backend startup step must expose its selected port'
Assert ($runtimeBlock.Contains('socket.socket')) 'qa-runtime must select a free port with Python socket'
Assert ($runtimeBlock.Contains('("127.0.0.1", 0)')) 'qa-runtime port selection must bind loopback port 0'
Assert ($runtimeBlock.Contains('echo "port=$backend_port" >> "$GITHUB_OUTPUT"')) 'qa-runtime must expose the selected port as a step output'
Assert ($runtimeBlock.Contains('SERVER_ADDRESS="127.0.0.1" SERVER_PORT="$backend_port" ./gradlew :backend:boot:bootRun')) 'qa-runtime backend must bind only loopback at the selected port'
Assert ($runtimeBlock.Contains('http://127.0.0.1:$backend_port/actuator/health')) 'qa-runtime health probe must use the selected port'
Assert ($runtimeBlock.Contains('BACKEND_URL: http://127.0.0.1:${{ steps.start-backend.outputs.port }}')) 'qa-runtime smoke must consume the backend startup port output'
Assert (-not $runtimeBlock.Contains('127.0.0.1:8080')) 'qa-runtime must not hard-code backend port 8080'

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
