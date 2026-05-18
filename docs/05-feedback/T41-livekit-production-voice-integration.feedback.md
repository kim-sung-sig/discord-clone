# T41 LiveKit Production Voice Integration Feedback

작성일: 2026-05-17  
Slice: T41 LiveKit Production Voice Integration

## Feedback Items

| ID | Severity | Finding | Follow-up |
| --- | --- | --- | --- |
| T41-FB-001 | Medium | LiveKit participant identity is user-scoped because `VoiceTokenSigningRequest` does not yet carry a session/device id. | Add session-aware voice token request after account/session hardening exposes stable session ids. |
| T41-FB-002 | Medium | Stage audience/speaker grants are not yet mapped to LiveKit-specific claim differences. | Extend media grant policy when stage media integration is implemented. |
| T41-FB-003 | Medium | Real LiveKit media track exchange was not run in CI. | Add optional local LiveKit harness and environment-gated two-browser media smoke. |
| T41-FB-004 | Low | Redaction tests prove API secret is not embedded in generated token, but shared log redaction does not yet match LiveKit JWT patterns. | Extend shared redaction tests to cover issued media tokens. |
