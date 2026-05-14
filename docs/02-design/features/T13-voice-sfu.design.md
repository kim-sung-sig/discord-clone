# T13 Voice/SFU Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T13 Voice/SFU

## Backend Architecture

Create `backend/modules/voice` for voice state and token issuing. The module deliberately does not import a LiveKit SDK yet; it emits deterministic `VoiceRoomToken` values with explicit `provider = LIVEKIT_SKELETON`. This keeps the test harness deterministic and avoids network/dependency churn while preserving the boundary needed for real LiveKit JWT replacement.

### Domain Files

- `VoiceParticipantState`: user/channel state with `muted`, `deafened`, `speaking`, `screenSharing`.
- `VoiceRoomToken`: room, participant, token, provider, expiresAt.
- `VoiceStateEvent`: gateway-facing event projection.
- `VoiceStateUpdateCommand`: update command for local state.
- `InMemoryVoiceService`: join/leave/update/token/event list.

### Boot Adapter Files

- `VoiceConfiguration`
- `VoiceController`
- `VoiceControllerTest`
- Modify boot dependencies/settings for `backend:modules:voice`.

## API Contract

- `POST /api/voice/channels/{channelId}/join`
  - Auth required.
  - Requires voice channel belongs to a guild and requester can view it.
  - Creates voice state and returns `VoiceJoinResponse` with token and participant state.
- `DELETE /api/voice/channels/{channelId}/leave`
  - Auth required.
  - Removes participant voice state.
- `PATCH /api/voice/channels/{channelId}/state`
  - Mutates mute/deaf/speaking/screen share booleans.
- `GET /api/voice/channels/{channelId}/participants`
  - Requires view permission.
- `GET /api/voice/events`
  - Test/support endpoint returning recent voice gateway event skeletons.

## Permission Model

The REST adapter owns guild/channel permission checks using `InMemoryGuildService`. Domain service receives only already-authorized commands. Token issuance is only possible through `join`, so a forbidden `VIEW_CHANNEL` check denies both token and state creation.

## Frontend Architecture

Add `VoicePanel.vue` to the Nuxt workspace and extend `shell.ts` with:

- active voice channel
- participant list
- local state booleans
- token status
- recent voice gateway events
- actions `joinVoiceChannel`, `leaveVoiceChannel`, `toggleMute`, `toggleDeaf`, `toggleSpeaking`, `toggleScreenShare`

The panel should render voice state independently from group DM call skeleton. It represents guild voice channel behavior.

## Test Strategy

- Domain TDD:
  - join returns state and token.
  - leave removes state.
  - mute/deaf/speaking/screen share updates emit events.
- REST TDD:
  - token only issued for visible voice channel.
  - denied channel returns 403 and no participant state.
  - leave removes participant.
- Frontend TDD:
  - component test for panel render/actions.
  - Playwright smoke for join, controls, screen share skeleton, leave.

## Risks

- Real two-browser media smoke is not feasible until actual WebRTC/SFU is connected. For T13, the browser test verifies signaling UI and state transitions only.
- Deterministic token must be clearly labeled non-production.
