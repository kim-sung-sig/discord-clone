$ErrorActionPreference = 'Stop'

$helperPath = Join-Path $PSScriptRoot 'process-tree-cleanup.ps1'
$realBackendPath = Join-Path $PSScriptRoot 'real-backend-e2e.ps1'
$dbCommonPath = Join-Path $PSScriptRoot 'db-drill-common.ps1'
$agentHarnessPath = Join-Path $PSScriptRoot 'agent-harness.ps1'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

foreach ($path in @($helperPath, $realBackendPath, $dbCommonPath, $agentHarnessPath)) {
  Assert (Test-Path $path) "Missing required process cleanup file: $path"

  $tokens = $null
  $parseErrors = $null
  [System.Management.Automation.Language.Parser]::ParseFile($path, [ref] $tokens, [ref] $parseErrors) | Out-Null
  Assert ($parseErrors.Count -eq 0) "$path has parse errors: $($parseErrors | ConvertTo-Json -Compress)"
}

$helper = Get-Content -Path $helperPath -Raw
$realBackend = Get-Content -Path $realBackendPath -Raw
$dbCommon = Get-Content -Path $dbCommonPath -Raw
$agentHarness = Get-Content -Path $agentHarnessPath -Raw

$requiredHelperSnippets = @(
  'function Test-QaIsWindows',
  'function Stop-QaProcessTree',
  'ParentProcessId=$ProcessId',
  'Get-CimInstance Win32_Process',
  'function Stop-QaListeningProcessByPort',
  'Get-NetTCPConnection -LocalPort $Port',
  'Where-Object { $_.State -eq ''Listen'' }',
  'Select-Object -ExpandProperty OwningProcess -Unique',
  'if ($owningProcess -le 0)',
  '$process.CommandLine -like $ExpectedCommandLinePattern',
  'Stop-QaProcessTree -ProcessId ([int] $owningProcess)',
  'Write-QaCleanupStep'
)

foreach ($snippet in $requiredHelperSnippets) {
  Assert ($helper.Contains($snippet)) "process-tree-cleanup.ps1 is missing required snippet: $snippet"
}

Assert ($realBackend.Contains(". (Join-Path `$PSScriptRoot 'process-tree-cleanup.ps1')")) 'real-backend-e2e.ps1 must dot-source process-tree-cleanup.ps1'
Assert ($realBackend.Contains('Stop-QaProcessTree -ProcessId $backendProcess.Id')) 'real-backend-e2e.ps1 must stop the backend wrapper through Stop-QaProcessTree'
Assert ($realBackend.Contains('Stop-QaListeningProcessByPort')) 'real-backend-e2e.ps1 must cleanup backend child listeners through Stop-QaListeningProcessByPort'
Assert ($realBackend.Contains('ExpectedCommandLinePattern ''*com.example.discord.DiscordApplication*''')) 'real-backend-e2e.ps1 must guard port cleanup by backend command line pattern'

Assert ($dbCommon.Contains(". (Join-Path `$PSScriptRoot 'process-tree-cleanup.ps1')")) 'db-drill-common.ps1 must dot-source process-tree-cleanup.ps1'
Assert ($dbCommon.Contains('Stop-QaProcessTree')) 'db-drill-common.ps1 must delegate process-tree cleanup to the shared helper'
Assert ($dbCommon.Contains('Stop-QaListeningProcessByPort')) 'db-drill-common.ps1 must delegate port cleanup to the shared helper'

Assert ($agentHarness.Contains('process-tree-cleanup-contract')) 'agent-harness.ps1 must expose the process cleanup contract as an allowlisted tool'
Assert ($agentHarness.Contains('qa/process-tree-cleanup.contract.ps1')) 'agent-harness.ps1 must wire the process cleanup contract script'

Write-Output 'PROCESS_TREE_CLEANUP_CONTRACT_PASS'
