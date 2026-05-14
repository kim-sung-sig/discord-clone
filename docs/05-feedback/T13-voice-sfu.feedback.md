# T13 Voice/SFU Feedback

작성일: 2026-05-14

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Architecture review | Do not add real LiveKit dependency before auth/state contract is stable. | Added deterministic `LIVEKIT_SKELETON` token provider and non-production token prefix. |
| Backend TDD | Forbidden voice channel must not create stale participant state. | Added MockMvc assertion that denied join leaves participant list empty. |
| Frontend TDD | Guild voice state must not be confused with group DM call skeleton. | Added dedicated `VoicePanel` and separate Pinia voice state. |
| E2E | Voice controls need SSR-safe interactivity. | Added mounted hydration guards to voice buttons. |

## Known Non-Blocking Risks

- No real WebRTC/LiveKit media connection yet.
- Voice state/event persistence is deferred.
- Real two-browser media smoke is deferred until media plane integration.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
