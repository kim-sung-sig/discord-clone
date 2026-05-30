$ErrorActionPreference = 'Continue'

$repoRoot = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $repoRoot 'infra/docker/docker-compose.yml'
$artifactDir = $env:CENTRAL_KAFKA_ARTIFACT_DIR
if ([string]::IsNullOrWhiteSpace($artifactDir)) {
  $artifactDir = Join-Path $repoRoot 'qa/artifacts/central-kafka/ci'
}
if (-not [System.IO.Path]::IsPathRooted($artifactDir)) {
  $artifactDir = Join-Path $repoRoot $artifactDir
}

New-Item -ItemType Directory -Force -Path $artifactDir | Out-Null

function Write-CapturedCommand($name, [scriptblock] $command) {
  $path = Join-Path $artifactDir $name
  try {
    & $command *>&1 | Out-File -FilePath $path -Encoding utf8
  } catch {
    "artifact command failed: $($_.Exception.Message)" | Out-File -FilePath $path -Encoding utf8
  }
}

function Copy-ArtifactDirectory($source, $targetName) {
  if (-not (Test-Path $source)) {
    return
  }
  $target = Join-Path $artifactDir $targetName
  New-Item -ItemType Directory -Force -Path $target | Out-Null
  Copy-Item -Path (Join-Path $source '*') -Destination $target -Recurse -Force -ErrorAction SilentlyContinue
}

Push-Location $repoRoot
try {
  Write-CapturedCommand 'docker-ps.txt' { docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' }
  Write-CapturedCommand 'docker-compose-ps.txt' { docker compose -f $compose ps }
  Write-CapturedCommand 'docker-compose-config.txt' { docker compose -f $compose config }
  Write-CapturedCommand 'docker-compose-ms-kafka.log' { docker compose -f $compose logs --no-color ms-kafka }
  Write-CapturedCommand 'docker-ms-kafka.log' { docker logs ms-kafka }

  Copy-ArtifactDirectory (Join-Path $repoRoot 'backend/boot/build/reports/tests/test') 'gradle-test-report'
  Copy-ArtifactDirectory (Join-Path $repoRoot 'backend/boot/build/test-results/test') 'gradle-test-results'

  Write-Output "CENTRAL_KAFKA_CI_ARTIFACTS_COLLECTED $artifactDir"
} finally {
  Pop-Location
}
