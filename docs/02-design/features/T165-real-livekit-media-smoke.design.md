# T165 Real LiveKit Media Smoke Design

Date: 2026-05-21

## Design

The smoke uses Playwright plus `livekit-client`. The backend remains the authorization and token issuer, and the browser test performs the actual media connection.

Flow:

1. Create two backend users.
2. Create a guild and voice channel.
3. Add the second user to the guild.
4. Call `/api/voice/channels/{channelId}/join` for both users.
5. Require provider `LIVEKIT`.
6. Load the LiveKit client in two browser pages.
7. Connect both pages to the LiveKit room with backend-issued tokens.
8. Publish synthetic canvas video from both pages.
9. Wait for `RoomEvent.TrackSubscribed` on both pages.

## Environment Gate

`LIVEKIT_MEDIA_SMOKE=1` is required. Without it, the smoke is skipped so regular CI and local test runs do not require a media server.

## Security

The test never uses camera or microphone devices. It publishes synthetic canvas frames and checks that configured LiveKit secrets are not present in the issued token JSON logged by the test process.
