# T13 Voice/SFU Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T13 Voice/SFU

## Problem

| 관점 | 내용 |
| --- | --- |
| User Problem | voice channel에 입장/퇴장, mute/deaf/speaking/screen share 상태, SFU token 발급 흐름이 없다. |
| Product Problem | Discord clone의 실시간 media 기능이 channel list skeleton에만 머물러 실제 voice session UX를 검증할 수 없다. |
| Engineering Problem | media plane은 SFU가 담당해야 하지만, Spring authorization/voice state/Gateway event boundary가 먼저 필요하다. |
| Core Value | T14 stage/soundboard 및 실제 LiveKit 연동이 사용할 voice state authority와 token boundary를 만든다. |

## Scope

- Voice state join/leave for guild voice channels.
- Permission checked token issuance for allowed voice channels only.
- Mute/deaf/speaking/screen share skeleton state mutation.
- Voice state Gateway event recording.
- Nuxt voice panel showing participants and local controls.

## Out of Scope

- Real WebRTC media publishing/subscribing.
- Real LiveKit server integration and signed production JWT.
- Device permission prompts, audio meters, echo cancellation.
- Two real browser audio media assertions.

## Success Criteria

- Backend voice permission test passes.
- Voice token is issued only for members allowed to view/join the voice channel.
- Leave removes voice state.
- Voice state Gateway event test passes.
- Frontend Playwright voice smoke covers join, mute/deaf/speaking, screen share skeleton, and leave.

## Failure Criteria

- Unauthorized user obtains voice token.
- Leave leaves stale voice state.
- UI can show connected after backend/store has no active voice state.
- Voice state changes do not produce a gateway event record.

## Assumptions

- `VIEW_CHANNEL` is the minimum voice join permission in this skeleton.
- The deterministic token is a non-production LiveKit placeholder and must not be treated as a real signed credential.
- Voice state is in-memory and per guild/channel/user.
