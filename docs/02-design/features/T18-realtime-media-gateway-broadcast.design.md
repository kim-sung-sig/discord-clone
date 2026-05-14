# T18 Realtime Media/Gateway Broadcast Design

작성일: 2026-05-15  
PDCA Phase: Design  
Slice: T18 Realtime Media/Gateway Broadcast Integration

## Architecture Decision

Keep Spring Boot on the realtime control plane only. The backend issues room-scoped LiveKit-compatible token material through a replaceable signer port, persists local voice/stage/soundboard state in the existing in-memory services, and publishes sanitized control events to the existing gateway service.

Gateway delivery authorization remains centralized in `InMemoryGatewayService.poll`. Every media-adjacent event must carry `guildId` and `channelId`; the gateway service then applies `guildService.canViewChannel(guildId, channelId, userId)` before delivering to each session.

## Token Boundary

Create the following voice module types:

- `VoiceTokenSigner`: provider interface for issuing room tokens.
- `VoiceTokenSigningRequest`: immutable request containing guild, channel, user, issued timestamp, and ttl.
- `SkeletonLiveKitTokenSigner`: deterministic local implementation using provider `LIVEKIT_SKELETON` and token prefix `NON_PRODUCTION_LIVEKIT_SKELETON:`.

`InMemoryVoiceService` depends on `VoiceTokenSigner` instead of constructing token strings itself. This makes the future production implementation a bean swap, not a controller/service rewrite.

## Gateway Event Contract

| Mutation | Gateway type | Channel scoped | Sensitive fields excluded |
| --- | --- | --- | --- |
| Voice join | `VOICE_STATE_UPDATE` | yes | token |
| Voice leave | `VOICE_STATE_UPDATE` | yes | token |
| Voice state patch | `VOICE_STATE_UPDATE` | yes | token |
| Stage session create | `STAGE_SESSION_UPDATE` | yes | none |
| Stage request to speak | `STAGE_SESSION_UPDATE` | yes | none |
| Stage speaker approval | `STAGE_SESSION_UPDATE` | yes | none |
| Stage move to audience | `STAGE_SESSION_UPDATE` | yes | none |
| Soundboard play | `SOUNDBOARD_SOUND_PLAY` | yes | objectKey contents excluded from event payload |

Payloads are small maps intended for client-side state reconciliation. They include IDs and state flags, not JWTs, access tokens, signed URLs, or LiveKit secrets.

## Controller Integration

- `VoiceController` injects `InMemoryGatewayService` and publishes after successful `voiceService.join`, `voiceService.leave`, and `voiceService.update`.
- `StageController` injects `InMemoryGatewayService` and publishes after each successful stage mutation.
- `SoundboardController` injects `InMemoryGatewayService` and publishes after successful sound play.

Publish after domain mutation only. Failed authorization, invalid channel, mismatched sound, or invalid stage state must not emit gateway events.

## Test Strategy

Backend MockMvc tests represent the current gateway transport as two logical browser sessions:

- identify session A and session B
- mutate media state through REST
- poll each session
- assert authorized session receives event
- assert denied channel session receives no event

Additional token signer unit coverage verifies the signer returns the documented provider, room, participant, expiry, and non-production prefix without adding real media credentials.

## Risks

- Poll-based gateway is not a real WebSocket transport; WebSocket upgrade remains a later gateway task.
- In-memory gateway events are single-node only; cross-node fanout still requires Redis/Kafka design.
- Skeleton signer is intentionally not production authentication; deployment must wire a real signer with secret management before real LiveKit use.
