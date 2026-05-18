# T41 LiveKit Production Voice Integration Analysis

작성일: 2026-05-17  
PDCA Phase: Check  
Slice: T41 LiveKit Production Voice Integration

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `.\\gradlew.bat :backend:modules:voice:test --tests com.example.discord.voice.LiveKitVoiceTokenSignerTest` | RED then PASS | RED failed because `LiveKitVoiceTokenSigner` and `LIVEKIT_PROVIDER` did not exist; PASS after signer/provider implementation. |
| `.\\gradlew.bat --no-daemon :backend:boot:test --tests com.example.discord.voice.VoiceConfigurationTest` | RED then PASS | RED failed because `VoiceTokenSigner` was not a Spring bean; PASS after profile-based signer wiring. |
| `.\\gradlew.bat --no-daemon :backend:boot:test --tests com.example.discord.ops.ProductionSecretValidationTest` | RED then PASS | RED failed because `media-livekit` secrets were not validated; PASS after production validation update. |
| `.\\gradlew.bat --no-daemon :backend:modules:voice:test :backend:boot:test --tests com.example.discord.voice.VoiceConfigurationTest --tests com.example.discord.ops.ProductionSecretValidationTest --tests com.example.discord.voice.VoiceControllerTest` | PASS | Voice module tests, production config validation, signer profile wiring, and existing voice controller behavior passed. |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| Only users with voice/stage channel permission receive LiveKit room tokens | PARTIAL | Existing `VoiceControllerTest.deniedVoiceChannelDoesNotIssueTokenOrCreateParticipantState` still passes; stage grant mapping is not implemented in this slice. |
| Token claims include user, session, guild, channel, and room scope without exposing backend secrets | PARTIAL | `LiveKitVoiceTokenSignerTest` verifies room, user, guild, channel, TTL, and secret non-exposure. Session id is not yet part of `VoiceTokenSigningRequest`. |
| Leave/revoke/session invalidation prevents future token issuance | PARTIAL | Existing leave behavior remains green; explicit session revoke/token issuance denial is still future work. |
| Media tokens, API keys, and secrets are redacted from logs and QA artifacts | PARTIAL | Token does not contain API secret. Dedicated log/artifact redaction for issued media tokens remains follow-up. |
| Two-browser smoke verifies join/leave media control path and Gateway voice state updates | PARTIAL | Existing voice controller/Gateway regression passed. Real LiveKit media smoke remains environment-gated. |
| Production profile refuses to enable LiveKit signer without explicit non-default API key/secret | PASS | `ProductionSecretValidationTest` now covers `media-livekit` API key, secret, and URL requirements. |

## Implementation Notes

- Added `LiveKitVoiceTokenSigner` in the voice module.
- Extended `VoiceRoomToken` to allow both `LIVEKIT_SKELETON` and `LIVEKIT` providers.
- Kept `SkeletonLiveKitTokenSigner` as the default non-production signer.
- Changed `VoiceConfiguration` to select `SkeletonLiveKitTokenSigner` by default and `LiveKitVoiceTokenSigner` under the `media-livekit` profile.
- Added production validation for `discord.media.livekit.api-key`, `discord.media.livekit.api-secret`, and `discord.media.livekit.url` when `media-livekit` is active.
- Updated `.env.example` to use Spring-compatible `DISCORD_MEDIA_LIVEKIT_*` environment variable names.

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| `VoiceTokenSigningRequest` has no session id | Token participant identity cannot yet include session scope from T41 design | Add session-aware token request after auth/session model exposes stable device/session id |
| Stage speaker/audience grants are not represented in LiveKit claims | Audience privilege boundaries are still controller/domain-only | Add stage-specific token grants when stage media room integration starts |
| No real LiveKit SFU smoke in CI | Token format and control plane are tested, not actual media track exchange | Add optional local LiveKit harness and environment-gated Playwright media smoke |
| Issued media token redaction is not globally tested | Future logs/artifacts might accidentally include token values | Extend shared redaction tests to include LiveKit JWT patterns |

## Decision

T41 is acceptable as the first production LiveKit integration slice. It establishes the signer, profile wiring, and production secret gate without changing the default local skeleton behavior. Full media smoke, session-scoped participant identity, and stage-specific grants remain follow-up items.
