$ErrorActionPreference = 'Stop'

$gatewayRoot = Join-Path (Split-Path -Parent $PSScriptRoot) 'infra/gateway'
$files = Get-ChildItem -Path $gatewayRoot -Recurse -File
if ($files.Count -eq 0) { throw 'infra/gateway must contain routing configuration' }

$forbidden = 'jwt|java|spring|datasource|postgres|password|kafka|redis|event-bus'
foreach ($file in $files) {
  if ($file.Extension -eq '.java') { throw "Gateway must not contain Java source: $($file.FullName)" }
  if ((Get-Content -Raw -Path $file.FullName) -match $forbidden) {
    throw "Gateway must not contain application, credential, database, or event-bus configuration: $($file.FullName)"
  }
}

Write-Output 'RUNTIME_SPLIT_GATEWAY_CONTRACT_PASS'
