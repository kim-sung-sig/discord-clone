# T41 LiveKit Production Voice Integration Plan

작성일: 2026-05-17  
PDCA Phase: Plan  
Slice: T41 LiveKit Production Voice Integration

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T13/T18은 voice state와 token signer boundary를 만들었지만, production secret-backed LiveKit token 발급과 실제 media room 권한 검증은 아직 skeleton 수준이다. |
| Solution | LiveKit API key/secret 기반 signer, room/participant claim policy, voice/stage permission mapping, two-browser media smoke, secret rotation guide를 추가한다. |
| Function UX Effect | 사용자는 권한 있는 voice/stage channel에 들어갈 때 실제 media room token을 받고, 권한이 없거나 퇴장한 사용자는 media room에 접근하지 못한다. |
| Core Value | Spring backend는 authorization/control plane만 소유하고, RTP media plane은 LiveKit에 맡기는 production-ready 경계를 확정한다. |

## Scope

- Add secret-backed `LiveKitVoiceTokenSigner` implementation.
- Keep deterministic/test signer for unit and local fixture tests.
- Define room naming policy for guild/channel/stage sessions.
- Define participant identity and claim policy using user/session/channel scope.
- Map guild/channel voice and stage permissions to LiveKit room token grants.
- Add config validation for LiveKit API key/secret in production media profile.
- Add token redaction tests so media secrets and issued tokens do not leak to logs/artifacts.
- Add two-browser media smoke where local LiveKit or mocked LiveKit-compatible endpoint is available.
- Document media secret deployment and rotation procedure.

## Out of Scope

- Implementing RTP/SFU media plane in Spring.
- Full LiveKit cluster deployment, TURN/STUN production topology, and autoscaling.
- Recording/transcription.
- Screen share beyond existing skeleton claim planning.
- Mobile native media SDK integration.

## Success Criteria

- Only users with voice/stage channel permission receive LiveKit room tokens.
- Token claims include user, session, guild, channel, and room scope without exposing backend secrets.
- Leave/revoke/session invalidation prevents future token issuance for that channel.
- Media tokens, API keys, and secrets are redacted from logs and QA artifacts.
- Two-browser smoke verifies join/leave media control path and Gateway voice state updates.
- Production profile refuses to enable LiveKit signer without explicit non-default API key/secret.

## Failure Criteria

- Unauthorized users can receive a LiveKit token for hidden or forbidden channels.
- Token payload or logs expose API secret, access token, refresh token, or signed media token.
- Spring attempts to proxy or process RTP media directly.
- Voice state says a user left but token issuance still succeeds for stale session state.
- T41 closes without a documented secret rotation and deployment guide.
