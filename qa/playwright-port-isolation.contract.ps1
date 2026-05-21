$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$playwrightConfigPath = Join-Path $repoRoot 'apps/web/playwright.config.ts'
$rootPackagePath = Join-Path $repoRoot 'package.json'
$webE2eScriptPath = Join-Path $PSScriptRoot 'web-e2e-isolated.mjs'
$agentHarnessPath = Join-Path $PSScriptRoot 'agent-harness.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

foreach ($path in @($playwrightConfigPath, $rootPackagePath, $webE2eScriptPath, $agentHarnessPath)) {
  Assert (Test-Path $path) "Missing required Playwright isolation file: $path"
}

$config = Get-Content -Path $playwrightConfigPath -Raw
$packageJson = Get-Content -Path $rootPackagePath -Raw
$webE2eScript = Get-Content -Path $webE2eScriptPath -Raw
$agentHarness = Get-Content -Path $agentHarnessPath -Raw

$requiredConfigSnippets = @(
  "const reuseExistingServer = process.env.PLAYWRIGHT_REUSE_EXISTING_SERVER === '1'",
  "reuseExistingServer",
  "reuseExistingServer: reuseExistingServer"
)

foreach ($snippet in $requiredConfigSnippets) {
  Assert ($config.Contains($snippet)) "apps/web/playwright.config.ts is missing required snippet: $snippet"
}

Assert (-not $config.Contains('reuseExistingServer: !process.env.CI')) 'Playwright config must not reuse existing localhost servers by default'

$requiredScriptSnippets = @(
  "createServer",
  "fileURLToPath",
  "const repoRoot = fileURLToPath(new URL('..', import.meta.url))",
  "server.listen(0, '127.0.0.1'",
  "NUXT_DEV_PORT",
  "PLAYWRIGHT_BASE_URL",
  "CI: '1'",
  "PLAYWRIGHT_REUSE_EXISTING_SERVER: '0'",
  "npm.cmd",
  "'--workspace'",
  "'@discord-clone/web'",
  "'--'",
  "const npmCliPath = process.env.npm_execpath",
  "const command = npmCliPath ? process.execPath : npmCommand",
  "const commandArgs = npmCliPath ? [npmCliPath, ...args] : args",
  "spawn(command, commandArgs, {",
  "process.exitCode = code ?? 1"
)

Assert (-not $webE2eScript.Contains('windowsCommand')) 'qa/web-e2e-isolated.mjs must not build a Windows shell command string'
Assert (-not $webE2eScript.Contains('shell: true')) 'qa/web-e2e-isolated.mjs must not use shell:true'
Assert (-not $webE2eScript.Contains('shell: process.platform')) 'qa/web-e2e-isolated.mjs must not use platform shell fallback'

foreach ($snippet in $requiredScriptSnippets) {
  Assert ($webE2eScript.Contains($snippet)) "qa/web-e2e-isolated.mjs is missing required snippet: $snippet"
}

Assert ($packageJson.Contains('"e2e": "node qa/web-e2e-isolated.mjs"')) 'root package e2e must use the isolated Playwright wrapper'
Assert ($packageJson.Contains('"e2e:web:raw": "npm run e2e --workspace @discord-clone/web"')) 'root package must keep an explicit raw web e2e escape hatch'
Assert ($packageJson.Contains('"e2e:web:isolated": "node qa/web-e2e-isolated.mjs"')) 'root package must expose the isolated web e2e script'

Assert ($agentHarness.Contains("'web-e2e' = New-Tool 'Run web Playwright e2e tests on an isolated port.'")) 'agent harness web-e2e tool must describe isolated-port behavior'
Assert ($agentHarness.Contains("@((Get-NpmCommand), 'run', 'e2e:web:isolated')")) 'agent harness web-e2e tool must call the isolated root script'
Assert ($agentHarness.Contains('playwright-port-isolation-contract')) 'agent harness must expose the Playwright port isolation contract'

Write-Output 'PLAYWRIGHT_PORT_ISOLATION_CONTRACT_PASS'
