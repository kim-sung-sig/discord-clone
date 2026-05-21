param(
  [string] $Tool,
  [switch] $List,
  [string] $ArtifactsDir = 'qa/artifacts/agent-harness'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$artifactRoot = if ([System.IO.Path]::IsPathRooted($ArtifactsDir)) { $ArtifactsDir } else { Join-Path $repoRoot $ArtifactsDir }
$statePath = Join-Path $artifactRoot 'agent-harness-state.json'

function Test-IsWindows {
  return [System.Environment]::OSVersion.Platform -eq 'Win32NT'
}

function Get-GradleWrapper {
  if (Test-IsWindows) {
    return Join-Path $repoRoot 'gradlew.bat'
  }
  return Join-Path $repoRoot 'gradlew'
}

function Get-NpmCommand {
  if (Test-IsWindows) {
    return 'npm.cmd'
  }
  return 'npm'
}

function Get-PowerShellCommand {
  if (Test-IsWindows) {
    return 'powershell'
  }
  return 'pwsh'
}

function New-Tool($description, $workingDirectory, $commandFactory) {
  return [pscustomobject]@{
    Description = $description
    WorkingDirectory = $workingDirectory
    CommandFactory = $commandFactory
  }
}

$allowedTools = [ordered]@{
  'backend-test' = New-Tool 'Run all backend Gradle tests.' $repoRoot {
    @((Get-GradleWrapper), 'test')
  }
  'backend-boot-test' = New-Tool 'Run backend boot module tests.' $repoRoot {
    @((Get-GradleWrapper), ':backend:boot:test')
  }
  'web-test' = New-Tool 'Run all npm workspace tests.' $repoRoot {
    @((Get-NpmCommand), 'test', '--workspaces')
  }
  'web-build' = New-Tool 'Build the Nuxt web workspace.' $repoRoot {
    @((Get-NpmCommand), 'run', 'build', '--workspace', '@discord-clone/web')
  }
  'web-e2e' = New-Tool 'Run web Playwright e2e tests.' $repoRoot {
    @((Get-NpmCommand), 'run', 'e2e', '--workspace', '@discord-clone/web')
  }
  'openapi-check' = New-Tool 'Check generated OpenAPI contract drift.' $repoRoot {
    @((Get-NpmCommand), 'run', 'openapi:check')
  }
  'docker-config' = New-Tool 'Render local Docker Compose config without starting containers.' $repoRoot {
    @('docker', 'compose', '-f', 'infra/docker/docker-compose.yml', 'config')
  }
  'api-smoke' = New-Tool 'Run repeatable API smoke checks against a running backend.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/api-smoke.ps1')
  }
  'real-backend-e2e-contract' = New-Tool 'Validate real backend e2e harness shape.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/real-backend-e2e.contract.ps1')
  }
  'real-backend-e2e' = New-Tool 'Run real backend API smoke and Playwright flow.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/real-backend-e2e.ps1')
  }
  'ci-workflow-contract' = New-Tool 'Validate GitHub Actions CI workflow shape.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/ci-workflow.contract.ps1')
  }
  'toolchain-warning-scan' = New-Tool 'Run Gradle and Nuxt warning budget scan.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/toolchain-warning-scan.ps1')
  }
  'migration-guard-contract' = New-Tool 'Validate migration guard harness shape.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/migration-guard.contract.ps1')
  }
  'backend-style-contract' = New-Tool 'Validate backend style, DDD, DTO, and layer boundaries.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/backend-style-contract.ps1')
  }
  'frontend-style-contract' = New-Tool 'Validate frontend style, storage, signature, and platform boundaries.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/frontend-style-contract.ps1')
  }
  'development-process-contract' = New-Tool 'Validate TDD and DDD process rules are visible to agents.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/development-process-contract.ps1')
  }
  'style-architecture-governance-contract' = New-Tool 'Validate style architecture governance docs and harness wiring.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/style-architecture-governance.contract.ps1')
  }
  'review-context-isolation-contract' = New-Tool 'Validate diff-only review context separation and commit/push policy.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/review-context-isolation.contract.ps1')
  }
  'task-complete-contract' = New-Tool 'Validate task completion commit/push helper shape.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/task-complete.contract.ps1')
  }
  'review-packet-contract' = New-Tool 'Validate diff-only review packet generator shape.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/review-packet.contract.ps1')
  }
  'real-lint-contract' = New-Tool 'Validate real backend/frontend lint tool wiring.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/real-lint.contract.ps1')
  }
  'process-tree-cleanup-contract' = New-Tool 'Validate QA process-tree cleanup helper wiring.' $repoRoot {
    @((Get-PowerShellCommand), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'qa/process-tree-cleanup.contract.ps1')
  }
  'frontend-lint' = New-Tool 'Run ESLint against frontend and shared TypeScript.' $repoRoot {
    @((Get-NpmCommand), 'run', 'lint:frontend')
  }
  'backend-lint' = New-Tool 'Run Gradle Checkstyle against backend Java sources.' $repoRoot {
    @((Get-NpmCommand), 'run', 'lint:backend')
  }
  'format-check' = New-Tool 'Run Prettier format check for lint configuration files.' $repoRoot {
    @((Get-NpmCommand), 'run', 'format:check')
  }
}

function Write-HarnessState($toolId, $result, $exitCode, $runDir, $logPath) {
  $state = [ordered]@{
    activeTask = $env:AGENT_TASK_ID
    phase = $env:AGENT_PDCA_PHASE
    lastTool = $toolId
    lastResult = $result
    exitCode = $exitCode
    artifactDir = $runDir
    logPath = $logPath
    nextAction = if ($result -eq 'PASS') { 'Record evidence in PDCA analysis/report.' } else { 'Record failure in feedback and rerun after a focused fix.' }
    blocked = $result -eq 'FAIL'
    updatedAt = (Get-Date).ToString('o')
  }
  $stateDirectory = Split-Path -Parent $statePath
  New-Item -ItemType Directory -Force -Path $stateDirectory | Out-Null
  $tempStatePath = Join-Path $stateDirectory "agent-harness-state.$PID.tmp"
  $state | ConvertTo-Json -Depth 4 | Set-Content -Path $tempStatePath -Encoding UTF8

  for ($attempt = 1; $attempt -le 5; $attempt++) {
    try {
      Move-Item -LiteralPath $tempStatePath -Destination $statePath -Force
      return
    } catch {
      if ($attempt -eq 5) {
        throw
      }
      Start-Sleep -Milliseconds (100 * $attempt)
    }
  }
}

if ($List) {
  foreach ($entry in $allowedTools.GetEnumerator()) {
    Write-Output "$($entry.Key) - $($entry.Value.Description)"
  }
  return
}

if ([string]::IsNullOrWhiteSpace($Tool)) {
  throw 'Tool is required. Use -List to see allowed tools.'
}

if (-not $allowedTools.Contains($Tool)) {
  throw "Unknown agent harness tool: $Tool"
}

New-Item -ItemType Directory -Force -Path $artifactRoot | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runDir = Join-Path $artifactRoot "$stamp-$Tool"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$logPath = Join-Path $runDir 'tool.log'

$toolDefinition = $allowedTools[$Tool]
$command = & $toolDefinition.CommandFactory

Start-Transcript -Path $logPath -Force | Out-Null
try {
  Write-Output "[agent-harness] tool=$Tool"
  Write-Output "[agent-harness] workingDirectory=$($toolDefinition.WorkingDirectory)"
  Write-Output "[agent-harness] command=$($command -join ' ')"

  Push-Location $toolDefinition.WorkingDirectory
  try {
    & $command[0] @($command | Select-Object -Skip 1)
    $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { $LASTEXITCODE }
  } finally {
    Pop-Location
  }

  if ($exitCode -ne 0) {
    Write-HarnessState $Tool 'FAIL' $exitCode $runDir $logPath
    throw "Agent harness tool failed: $Tool exitCode=$exitCode log=$logPath"
  }

  Write-HarnessState $Tool 'PASS' 0 $runDir $logPath
  Write-Output "AGENT_HARNESS_TOOL_PASS $Tool"
} catch {
  if (-not (Test-Path $statePath)) {
    Write-HarnessState $Tool 'FAIL' 1 $runDir $logPath
  }
  throw
} finally {
  Stop-Transcript | Out-Null
}
