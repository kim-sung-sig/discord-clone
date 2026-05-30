$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$specPath = Join-Path $repoRoot 'apps/web/tests/e2e/livekit-media.spec.ts'
$scriptPath = Join-Path $repoRoot 'qa/livekit-media-smoke.ps1'
$runbookPath = Join-Path $repoRoot 'docs/runbooks/livekit-media-smoke.md'
$webPackagePath = Join-Path $repoRoot 'apps/web/package.json'
$composePath = Join-Path $repoRoot 'infra/docker/docker-compose.yml'
$envExamplePath = Join-Path $repoRoot '.env.example'

function Assert($condition, $message) {
  if (-not $condition) {
    throw $message
  }
}

Assert (Test-Path $specPath) 'LiveKit media Playwright smoke spec is missing'
Assert (Test-Path $scriptPath) 'LiveKit media smoke runner is missing'
Assert (Test-Path $runbookPath) 'LiveKit media smoke runbook is missing'
Assert (Test-Path $webPackagePath) 'web package.json is missing'
Assert (Test-Path $composePath) 'docker compose file is missing'
Assert (Test-Path $envExamplePath) '.env.example is missing'

$spec = Get-Content -Path $specPath -Raw
$script = Get-Content -Path $scriptPath -Raw
$runbook = Get-Content -Path $runbookPath -Raw
$webPackage = Get-Content -Path $webPackagePath -Raw
$compose = Get-Content -Path $composePath -Raw
$envExample = Get-Content -Path $envExamplePath -Raw

$requiredSpecSnippets = @(
  'LIVEKIT_MEDIA_SMOKE',
  'LIVEKIT_URL',
  'REAL_BACKEND_BASE_URL',
  "require.resolve('livekit-client')",
  'RoomEvent.TrackSubscribed',
  'canvas.captureStream',
  'publishTrack',
  "provider).toBe('LIVEKIT')",
  'doesNotContain'
)

foreach ($snippet in $requiredSpecSnippets) {
  Assert ($spec.Contains($snippet)) "LiveKit media spec is missing required snippet: $snippet"
}

$requiredScriptSnippets = @(
  'LIVEKIT_MEDIA_SMOKE',
  'REAL_BACKEND_BASE_URL',
  'LIVEKIT_URL',
  'npm run e2e -w apps/web -- livekit-media.spec.ts',
  '$LASTEXITCODE'
)

foreach ($snippet in $requiredScriptSnippets) {
  Assert ($script.Contains($snippet)) "LiveKit media smoke runner is missing required snippet: $snippet"
}

$requiredRunbookSnippets = @(
  'Real LiveKit media smoke',
  'livekit-server --dev',
  'DISCORD_MEDIA_LIVEKIT_API_KEY',
  'DISCORD_MEDIA_LIVEKIT_API_SECRET',
  'LIVEKIT_MEDIA_SMOKE=1',
  'TrackSubscribed',
  'Do not commit LiveKit secrets',
  'No issued LiveKit JWT should be copied into logs or artifacts'
)

foreach ($snippet in $requiredRunbookSnippets) {
  Assert ($runbook.Contains($snippet)) "LiveKit media smoke runbook is missing required snippet: $snippet"
}

Assert ($webPackage.Contains('"livekit-client"')) 'web package must include livekit-client for real media smoke'
Assert ($compose.Contains('livekit/livekit-server')) 'docker compose must expose an optional LiveKit server'
Assert ($compose.Contains('media-livekit')) 'docker compose LiveKit service must be behind the media-livekit profile'
Assert ($envExample.Contains('LIVEKIT_MEDIA_SMOKE=0')) '.env.example must document the disabled-by-default media smoke flag'

Write-Output 'LIVEKIT_MEDIA_SMOKE_CONTRACT_PASS'
