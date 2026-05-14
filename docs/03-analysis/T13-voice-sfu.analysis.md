# T13 Voice/SFU Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T13 Voice/SFU

## Design Match

| Requirement | Status | Evidence |
| --- | --- | --- |
| voice state join/leave | Met | `InMemoryVoiceServiceTest`, `VoiceControllerTest.leaveRemovesParticipantState`, and Nuxt voice panel cover join/leave cleanup |
| LiveKit room token skeleton | Met | `VoiceRoomToken` emits provider `LIVEKIT_SKELETON` and non-production token prefix |
| token permission check | Met | `VoiceControllerTest.deniedVoiceChannelDoesNotIssueTokenOrCreateParticipantState` verifies denied channel returns 403 and no state |
| mute/deaf/speaking | Met | backend state update test and frontend VoicePanel controls cover all booleans |
| screen share skeleton | Met | backend `screenSharing` state and Playwright voice smoke cover toggle |
| voice state Gateway event skeleton | Met | `VoiceStateEvent` records JOIN/LEFT/UPDATE and REST event list test verifies update event |
| voice UI smoke | Met | Playwright `joins voice, toggles controls, screen shares, and leaves` passes |

## Gap Log

- Resolved: real LiveKit token signing would add external dependency and secrets before authorization semantics were stable. Implemented deterministic `LIVEKIT_SKELETON` token boundary.
- Resolved: voice join could have created stale state on forbidden channels. REST test verifies forbidden token request creates no participant.
- Resolved: frontend needed guild voice UI distinct from group DM call skeleton. Added dedicated `VoicePanel` and Pinia voice state/actions.

## Residual Risks

- No real WebRTC media plane, device capture, or LiveKit room connection yet.
- Voice state/event storage is in-memory.
- `/api/voice/events` is a test/support skeleton and not scoped by guild yet.
- Two-browser media smoke is represented by signaling UI smoke only.
