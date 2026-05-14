# T18 Realtime Media/Gateway Broadcast Analysis

작성일: 2026-05-15  
PDCA Phase: Check  
Slice: T18 Realtime Media/Gateway Broadcast Integration

## Verification Evidence

| Command | Result | Evidence |
| --- | --- | --- |
| `./gradlew.bat :backend:modules:voice:test --tests com.example.discord.voice.InMemoryVoiceServiceTest --rerun-tasks` before implementation | RED | compile failed because `SkeletonLiveKitTokenSigner` and `VoiceTokenSigningRequest` did not exist |
| `./gradlew.bat :backend:modules:voice:test --tests com.example.discord.voice.InMemoryVoiceServiceTest --rerun-tasks` after implementation | PASS | voice signer/service targeted tests passed |
| `./gradlew.bat :backend:boot:test --tests com.example.discord.voice.VoiceControllerTest --rerun-tasks` | PASS | voice gateway fanout and hidden-channel non-delivery test passed |
| `./gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest --rerun-tasks` before implementation | RED | new stage and soundboard gateway assertions failed because no gateway events were published |
| `./gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest --rerun-tasks` after implementation | PASS | stage and soundboard gateway fanout tests passed, including hidden-channel non-delivery |
| `./gradlew.bat test` | PASS | full backend test suite passed; build successful |

## Success Criteria Review

| Criteria | Status | Evidence |
| --- | --- | --- |
| LiveKit-compatible token signing is behind a replaceable provider interface | PASS | `VoiceTokenSigner`, `VoiceTokenSigningRequest`, `SkeletonLiveKitTokenSigner` added |
| Voice join/update/leave publish gateway events without embedding voice tokens | PASS | `VoiceControllerTest.voiceMutationsPublishGatewayEventsOnlyToChannelVisibleSessions` asserts gateway payload has no `token` |
| Soundboard play publishes a gateway event to voice-channel-visible sessions | PASS | `ExperienceControllerTest.soundboardPlayPublishesGatewayEventToVisibleChannelSessions` asserts `SOUNDBOARD_SOUND_PLAY` delivery |
| Stage changes publish gateway events by stage channel visibility | PASS | `ExperienceControllerTest.stageMutationsPublishGatewayEventsToVisibleChannelSessions` asserts stage create/request/approve delivery |
| Hidden-channel users do not receive voice/stage/soundboard events | PASS | voice hidden-channel non-delivery and stage/soundboard hidden-channel non-delivery are covered by MockMvc gateway poll tests |
| Targeted backend tests pass, then full backend suite passes | PASS | targeted voice, targeted experience, and full `gradlew test` passed |

## Gap Analysis

| Gap | Impact | Follow-up |
| --- | --- | --- |
| Poll gateway remains the transport | Medium | WebSocket gateway transport is still deferred from T05 and should remain a separate task |
| LiveKit signer is deterministic skeleton only | Expected | Production LiveKit signer must be wired with secret management before real media deployment |
| Cross-node fanout is not implemented | Medium | Redis/Kafka event bus remains future enterprise scaling work |

## Decision

T18 backend slice is acceptable for current roadmap scope. It connects media-adjacent REST mutations to gateway fanout, keeps secrets out of payloads/tests, and preserves the external SFU boundary.

