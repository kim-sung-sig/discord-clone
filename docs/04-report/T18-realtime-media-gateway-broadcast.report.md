# T18 Realtime Media/Gateway Broadcast Report

작성일: 2026-05-15  
PDCA Phase: Report  
Slice: T18 Realtime Media/Gateway Broadcast Integration

## Summary

T18 implemented the realtime control-plane bridge for voice, stage, and soundboard. REST mutations now emit sanitized, channel-scoped gateway events, and voice token issuance is isolated behind a `VoiceTokenSigner` provider interface.

## Delivered

- Added `VoiceTokenSigner` boundary and deterministic `SkeletonLiveKitTokenSigner`.
- Updated `InMemoryVoiceService` to request tokens through the signer instead of constructing tokens internally.
- Published `VOICE_STATE_UPDATE` gateway events for voice join, state update, and leave.
- Published `STAGE_SESSION_UPDATE` gateway events for stage session create, request-to-speak, speaker approval, and audience move.
- Published `SOUNDBOARD_SOUND_PLAY` gateway events for soundboard playback.
- Added MockMvc tests that model two logical browser sessions through gateway identify/poll, including hidden-channel non-delivery.
- Added token signer unit coverage.

## Safety Notes

- Gateway payloads do not include voice tokens, access tokens, signed URLs, object keys, or real media secrets.
- Spring still does not implement SFU/media plane behavior.
- Delivery authorization remains centralized in `InMemoryGatewayService.poll` through channel visibility checks.

## Test Evidence

- `./gradlew.bat :backend:modules:voice:test --tests com.example.discord.voice.InMemoryVoiceServiceTest --rerun-tasks`: PASS
- `./gradlew.bat :backend:boot:test --tests com.example.discord.voice.VoiceControllerTest --rerun-tasks`: PASS
- `./gradlew.bat :backend:boot:test --tests com.example.discord.experience.ExperienceControllerTest --rerun-tasks`: PASS, including stage/soundboard hidden-channel non-delivery
- `./gradlew.bat test`: PASS

## Commits

- `f09398e docs: plan T18 realtime gateway broadcast`
- `1e431a4 feat: broadcast voice gateway events`
- `d7456a4 feat: broadcast experience gateway events`
- `c070c54 test: cover media gateway visibility`

## Residual Risks

- Real WebSocket transport and cross-node fanout remain deferred.
- Production LiveKit signing still needs a secret-backed implementation and deployment secret policy.

## Next Recommended Task

Proceed to the next remaining roadmap task after T18, prioritizing either gateway WebSocket transport hardening or frontend realtime state reconciliation depending on the current task list order.



