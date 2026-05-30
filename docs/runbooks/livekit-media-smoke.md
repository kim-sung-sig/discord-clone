# Real LiveKit media smoke

Date: 2026-05-21
Scope: Prove that two authorized Discord Clone voice participants can connect to a real LiveKit room and exchange a synthetic video track.

## Purpose

The normal API and browser smoke tests prove the voice control plane. This smoke proves the media plane by connecting two browser participants through LiveKit and waiting for `TrackSubscribed` on both sides.

## Local LiveKit

Start a local LiveKit server in development mode:

```powershell
livekit-server --dev --bind 0.0.0.0
```

For Docker-based local work, use the optional Compose profile:

```powershell
docker compose -f infra/docker/docker-compose.yml --profile media-livekit up -d livekit
```

Use a local key/secret that matches the backend signer. `DISCORD_MEDIA_LIVEKIT_API_SECRET` must be at least 32 characters because the backend signer rejects shorter secrets.

```powershell
$env:DISCORD_MEDIA_LIVEKIT_API_KEY='devkey'
$env:DISCORD_MEDIA_LIVEKIT_API_SECRET='livekit-local-secret-at-least-32-characters'
$env:DISCORD_MEDIA_LIVEKIT_URL='ws://127.0.0.1:7880'
```

## Backend

Run the backend with the real LiveKit signer profile and the same key/secret:

```powershell
$env:SPRING_PROFILES_ACTIVE='postgres,redis,media-livekit'
$env:DISCORD_MEDIA_LIVEKIT_API_KEY='devkey'
$env:DISCORD_MEDIA_LIVEKIT_API_SECRET='livekit-local-secret-at-least-32-characters'
$env:DISCORD_MEDIA_LIVEKIT_URL='ws://127.0.0.1:7880'
.\gradlew.bat :backend:boot:bootRun
```

## Smoke

Run the environment-gated smoke:

```powershell
$env:LIVEKIT_MEDIA_SMOKE=1
$env:REAL_BACKEND_BASE_URL='http://127.0.0.1:8080'
$env:LIVEKIT_URL='ws://127.0.0.1:7880'
pwsh qa/livekit-media-smoke.ps1
```

Expected result:

- The backend voice join response uses provider `LIVEKIT`.
- Both participants connect to the same LiveKit room.
- Both participants publish a synthetic canvas video track.
- Both participants observe `TrackSubscribed` for the other participant.

## Security Rules

- Do not commit LiveKit secrets.
- No issued LiveKit JWT should be copied into logs or artifacts.
- The smoke uses synthetic canvas video and does not require camera or microphone permission.
- Keep the smoke disabled by default with `LIVEKIT_MEDIA_SMOKE=0`.
