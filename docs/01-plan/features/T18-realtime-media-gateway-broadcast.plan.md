# T18 Realtime Media/Gateway Broadcast Plan

작성일: 2026-05-15  
PDCA Phase: Plan  
Slice: T18 Realtime Media/Gateway Broadcast Integration

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | voice, stage, soundboard 도메인 상태는 REST로 변경되지만 gateway 세션으로 fanout되지 않아 다중 클라이언트 동기화가 끊긴다. voice token도 서비스 내부 문자열 생성으로 고정되어 있어 LiveKit 교체 경계가 불명확하다. |
| Solution | LiveKit 호환 토큰 발급을 `VoiceTokenSigner` 포트로 분리하고, voice/stage/soundboard 변경 성공 시 `InMemoryGatewayService.publish`로 채널 스코프 이벤트를 발행한다. |
| Function UX Effect | 한 사용자의 음성 입장/상태 변경, stage speaker 상태, soundboard 재생이 다른 클라이언트 gateway poll에 즉시 나타난다. 숨김 채널 사용자는 기존 gateway 권한 필터로 이벤트를 받지 못한다. |
| Core Value | Spring은 미디어 제어면만 담당하고 SFU/media plane은 외부 LiveKit 경계로 남겨 엔터프라이즈 확장 경로를 보존한다. |

## Scope

- Voice token generation boundary:
  - `VoiceTokenSigner` interface
  - deterministic `SkeletonLiveKitTokenSigner` implementation
  - no production secret or real LiveKit dependency in tests
- Gateway fanout from REST mutations:
  - voice join
  - voice leave
  - voice state update
  - stage session create
  - stage request to speak
  - stage speaker approval
  - stage audience move
  - soundboard sound play
- Authorization model:
  - mutation endpoints keep existing permission checks
  - published events include guild/channel scope
  - gateway poll remains the single delivery authorization boundary via `canViewChannel`
- Test coverage:
  - voice fanout visible to authorized session
  - voice fanout hidden from channel-denied member
  - soundboard play publishes channel-scoped event
  - stage state changes publish channel-scoped events
  - token signer boundary keeps non-production provider and avoids secret leakage

## Out of Scope

- Real WebRTC media negotiation.
- Running LiveKit/SFU containers.
- Redis/Kafka gateway cross-node fanout.
- WebSocket transport replacement for the existing poll-based gateway.
- Browser microphone/screen capture permissions.

## Success Criteria

- LiveKit-compatible token signing is behind a replaceable provider interface.
- Voice join/update/leave publish gateway events without embedding voice tokens.
- Soundboard play publishes a gateway event to voice-channel participants by channel visibility.
- Stage create/request/approve/audience changes publish gateway events by stage channel visibility.
- Hidden-channel users do not receive voice/stage/soundboard events through gateway poll.
- Targeted backend tests pass, then full backend test suite passes.

## Failure Criteria

- Real media secrets appear in source, docs, tests, or gateway payloads.
- Spring attempts to host SFU/media plane behavior directly.
- Events are published without channel scope where channel visibility is required.
- Hidden-channel users receive voice, stage, or soundboard events.
- Tests only assert REST response success and do not verify gateway delivery.
