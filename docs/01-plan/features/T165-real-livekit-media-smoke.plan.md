# T165 Real LiveKit Media Smoke Plan

Date: 2026-05-21

## Goal

Prove that Discord Clone voice participants can use real LiveKit media transport, not only the voice control-plane skeleton.

## Scope

- Add an environment-gated Playwright smoke for real LiveKit media.
- Add a smoke runner and contract.
- Add local runbook guidance for LiveKit server, backend profile, and secret handling.
- Add optional local Docker Compose LiveKit service.

## Acceptance Criteria

- The default e2e run skips the media smoke unless `LIVEKIT_MEDIA_SMOKE=1`.
- When enabled with a real LiveKit server and `media-livekit` backend, two authorized voice participants connect to the same room.
- Each browser participant publishes a synthetic canvas video track.
- Each browser participant receives `TrackSubscribed` for the other participant.
- The smoke does not commit or print LiveKit secrets.
