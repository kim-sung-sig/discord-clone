$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$artifactScript = Join-Path $repoRoot 'qa/central-kafka-ci-artifacts.ps1'
$workflow = Join-Path $repoRoot '.github/workflows/ci.yml'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $artifactScript) 'central Kafka CI artifact script is missing'
Assert (Test-Path $workflow) 'CI workflow is missing'

$scriptText = Get-Content -Path $artifactScript -Raw
Assert ($scriptText.Contains('CENTRAL_KAFKA_ARTIFACT_DIR')) 'artifact script must support CENTRAL_KAFKA_ARTIFACT_DIR'
Assert ($scriptText.Contains('docker ps')) 'artifact script must collect Docker container state'
Assert ($scriptText.Contains('docker compose')) 'artifact script must collect Compose state'
Assert ($scriptText.Contains('ms-kafka')) 'artifact script must collect ms-kafka logs'
Assert ($scriptText.Contains('backend/boot/build/reports/tests/test')) 'artifact script must copy Gradle HTML test reports'
Assert ($scriptText.Contains('backend/boot/build/test-results/test')) 'artifact script must copy Gradle XML test results'

$workflowText = Get-Content -Path $workflow -Raw
Assert ($workflowText.Contains('Collect central Kafka failure artifacts')) 'CI workflow must collect Kafka failure artifacts'
Assert ($workflowText.Contains('pwsh qa/central-kafka-ci-artifacts.ps1')) 'CI workflow must run Kafka artifact script'
Assert ($workflowText.Contains('Upload central Kafka artifacts')) 'CI workflow must upload Kafka artifacts'
Assert ($workflowText.Contains('central-kafka-artifacts')) 'CI workflow must name Kafka artifacts'
Assert ($workflowText.Contains('qa/artifacts/central-kafka')) 'CI workflow must upload the central Kafka artifact directory'
Assert ($workflowText.Contains('CENTRAL_KAFKA_ARTIFACT_DIR: qa/artifacts/central-kafka/ci')) 'CI workflow must set Kafka artifact directory'

Write-Output 'CENTRAL_KAFKA_CI_ARTIFACTS_CONTRACT_PASS'
