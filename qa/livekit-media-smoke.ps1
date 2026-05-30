$ErrorActionPreference = 'Stop'

function Require-Env($name) {
  if (-not [Environment]::GetEnvironmentVariable($name)) {
    throw "$name is required for the real LiveKit media smoke"
  }
}

Require-Env 'LIVEKIT_MEDIA_SMOKE'
Require-Env 'REAL_BACKEND_BASE_URL'
Require-Env 'LIVEKIT_URL'

if ($env:LIVEKIT_MEDIA_SMOKE -ne '1') {
  throw 'LIVEKIT_MEDIA_SMOKE must be 1 to run the real media smoke'
}

npm run e2e -w apps/web -- livekit-media.spec.ts
if ($LASTEXITCODE -ne 0) {
  exit $LASTEXITCODE
}
