# T41 LiveKit Production Voice Integration Design

작성일: 2026-05-17  
PDCA Phase: Design  
Slice: T41 LiveKit Production Voice Integration

## Architecture Decision

Use LiveKit as the media plane and keep Spring as the authorization/control plane. Spring issues short-lived LiveKit room tokens only after checking identity, session state, guild membership, channel visibility, and voice/stage permissions. LiveKit owns RTP, codecs, peer routing, and media quality.

Do not route media packets through Spring.

## Component Boundary

| Component | Responsibility |
| --- | --- |
| `VoiceService` | voice/stage join intent, leave state, permission checks |
| `VoiceTokenSigner` | abstract token signer boundary |
| `SkeletonLiveKitTokenSigner` | deterministic non-secret test/local signer |
| `LiveKitVoiceTokenSigner` | production JWT signer using API key/secret |
| `MediaRoomPolicy` | room name, participant identity, grant construction |
| `MediaSecretProperties` | API key/secret/config validation |
| Gateway | voice/stage control-plane events only, no media token fanout |

## Room Naming Policy

Room names should be deterministic, scoped, and non-secret:

```text
guild:{guildId}:voice:{channelId}
guild:{guildId}:stage:{channelId}
dm:{channelId}:voice
```

Rules:

- Use ids, not display names.
- Do not include user tokens, invite codes, or secret values.
- Stage and voice rooms are separate namespaces.
- Room name generation is centralized and covered by tests.

## Participant Identity and Claims

Participant identity:

```text
user:{userId}:session:{sessionId}
```

Token claims:

- `roomJoin`: true only for allowed channel.
- `room`: generated room name.
- `identity`: participant identity.
- `name`: safe display name or user id fallback.
- metadata: minimal JSON containing `guildId`, `channelId`, `userId`, and capability flags.

Claim restrictions:

- no backend access token,
- no refresh token,
- no database credentials,
- no storage object keys,
- no request headers.

## Authorization Policy

Token issuance requires:

1. Authenticated user session is valid.
2. User is a member of the guild or allowed DM/group DM voice context.
3. User can view the channel.
4. User has voice connect permission for voice rooms.
5. Stage speakers need stage speak/moderator permission; audience tokens can join with listen-only grants.
6. User is not blocked by moderation/session revoke policy.

Leave/session revoke behavior:

- Leave updates server voice state and emits Gateway event.
- Future token issuance for the stale session/channel must fail.
- Existing LiveKit token revocation depends on token TTL and provider-side room controls; TTL should be short and documented.

## Configuration Contract

Profiles:

```text
local/test: SkeletonLiveKitTokenSigner
media-livekit: LiveKitVoiceTokenSigner
production: requires media-livekit when real voice is enabled
```

Required properties:

```text
discord.media.livekit.api-key
discord.media.livekit.api-secret
discord.media.livekit.url
discord.media.livekit.token-ttl-seconds
```

Validation:

- API key and secret are required for `media-livekit`.
- API secret must not equal known test/default values.
- TTL must be short and bounded.
- URL must be explicit and not a placeholder in production.

## Gateway and Payload Safety

Gateway events may include:

- user id,
- channel id,
- guild id,
- voice state,
- mute/deaf/speaking flags,
- stage role/state.

Gateway events must not include:

- LiveKit room token,
- LiveKit API key/secret,
- Spring access/refresh token,
- signed URLs,
- storage object keys.

Clients fetch media tokens through authorized REST calls only.

## Two-browser Media Smoke

Preferred smoke levels:

1. Control-plane smoke: browser A joins voice, browser B sees voice state via Gateway.
2. Token smoke: browser A receives a syntactically valid LiveKit token with expected room/identity claims.
3. Media smoke: with local LiveKit available, browser A and B join same room and exchange track-ready events.

If LiveKit is unavailable in CI, keep media smoke environment-gated and require control/token smoke in CI.

## Secret Rotation Guide

1. Create new LiveKit API key/secret in provider console.
2. Store new secret in the configured secret manager under a versioned name.
3. Deploy backend with new key/secret and short token TTL.
4. Verify token issuance and two-browser smoke.
5. Revoke old key after all old tokens expire.
6. Confirm logs/artifacts contain only redacted secret references.

## QA Strategy

- Unit tests for room naming and participant claim construction.
- Unit tests for voice/stage permission to LiveKit grant mapping.
- Config validation tests for missing/default LiveKit secrets.
- Redaction tests for LiveKit API secret and issued tokens.
- Controller tests for unauthorized token denial.
- Two-browser smoke for control plane; optional real media smoke when LiveKit is available.

## Risks

- Real LiveKit media smoke can be environment-sensitive; keep CI-required smoke focused on token/control path unless the local SFU is stable.
- Token revocation is TTL-bound unless provider-side participant removal is added.
- Stage listen-only vs speaker grants must be explicit to avoid audience privilege escalation.
- Native mobile media SDK integration remains separate from this backend/control-plane task.
