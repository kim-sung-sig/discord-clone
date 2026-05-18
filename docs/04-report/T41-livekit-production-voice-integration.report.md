# T41 LiveKit Production Voice Integration Report

작성일: 2026-05-17  
PDCA Phase: Report  
Slice: T41 LiveKit Production Voice Integration

## Summary

T41 added the first production LiveKit integration slice. The backend can now select a secret-backed LiveKit token signer through the `media-livekit` profile while preserving the deterministic skeleton signer for default local/test execution.

## Delivered

- Added `LiveKitVoiceTokenSigner`.
- Added `LIVEKIT` as an accepted `VoiceRoomToken` provider.
- Kept `SkeletonLiveKitTokenSigner` as default non-production behavior.
- Added profile-based `VoiceTokenSigner` wiring in `VoiceConfiguration`.
- Added production validation for LiveKit API key, API secret, and URL when `media-livekit` is active.
- Updated `.env.example` with Spring-compatible `DISCORD_MEDIA_LIVEKIT_*` variables.
- Added tests for JWT shape, room/participant claims, secret non-exposure, Spring profile wiring, and production validation.

## Test Evidence

- `:backend:modules:voice:test --tests com.example.discord.voice.LiveKitVoiceTokenSignerTest`: RED then PASS
- `:backend:boot:test --tests com.example.discord.voice.VoiceConfigurationTest`: RED then PASS
- `:backend:boot:test --tests com.example.discord.ops.ProductionSecretValidationTest`: RED then PASS
- `:backend:modules:voice:test :backend:boot:test --tests com.example.discord.voice.VoiceConfigurationTest --tests com.example.discord.ops.ProductionSecretValidationTest --tests com.example.discord.voice.VoiceControllerTest`: PASS

## Residual Risks

- Real LiveKit SFU media smoke is still environment-gated and not wired into CI.
- Participant identity is user-scoped today; session-scoped identity needs a stable session id in the voice token request.
- Stage audience/speaker-specific LiveKit grants remain a follow-up.
- Global log/artifact redaction should explicitly cover issued LiveKit JWT patterns.

## Next Recommended Task

Proceed to T42 OpenAPI & Frontend Client Contract implementation, unless the immediate priority is deepening T41 with a local LiveKit smoke harness.
