# T14 Stage/Soundboard/Premium Skeleton Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T14 Stage/Soundboard/Premium Skeleton

## Problem

| 관점 | 내용 |
| --- | --- |
| User Problem | Discord의 stage channel, soundboard, premium gate 흐름이 없어 voice 이후의 community experience를 검증할 수 없다. |
| Product Problem | 음성 채널은 입장/상태까지만 존재하고, 발표/청중 운영, 서버 사운드, 유료 기능 제한 같은 실제 Discord 유지/수익화 기능이 빠져 있다. |
| Engineering Problem | Stage/Soundboard/Premium은 permission, voice, catalog, entitlement가 교차하므로 먼저 명확한 도메인 경계와 테스트 가능한 skeleton이 필요하다. |
| Core Value | T15+ 실제 media, billing, quest 확장 전에 권한 기반 experience gate와 UI smoke harness를 만든다. |

## Scope

- Stage session topic lifecycle with moderator, speaker, audience roles.
- Request-to-speak and moderator approval/move-to-audience transitions.
- Soundboard sound registration and play event skeleton for voice channels.
- Entitlement model and premium feature gate check.
- Shop/catalog skeleton and quests skeleton response.
- Nuxt experience panel covering stage, soundboard, premium gate flows.

## Out of Scope

- Real audio mixing, live speaker media routing, and WebRTC publish/subscription changes.
- Real payment provider, subscription lifecycle, refunds, taxes, and fraud workflow.
- Quest ad delivery, tracking SDK, rewards settlement, and external campaign integration.
- Persistent database tables. T14 remains in-memory to keep fast TDD feedback.

## Success Criteria

- Backend stage state transition test passes: audience cannot speak until moderator approval.
- Backend soundboard permission test passes: sound creation/play is denied without required permission/channel access.
- Backend entitlement feature gate test passes: premium feature is false before entitlement and true after grant.
- Frontend stage UI E2E passes: request to speak, approve speaker, move to audience, play sound, verify premium gate.
- Full backend and frontend smoke suites continue to pass.

## Failure Criteria

- Audience can become speaker without moderator approval.
- A user without channel access or soundboard permission can create/play soundboard sounds.
- Premium feature gate returns enabled without a valid entitlement.
- UI shows stage/soundboard/premium success while store state says denied or inactive.
- Skeleton token/entitlement wording implies production billing or real media delivery.

## Assumptions

- Stage moderator permission maps to guild owner or `MANAGE_CHANNELS` in the skeleton.
- Soundboard sound creation requires `MANAGE_EXPRESSIONS`; play requires channel visibility plus existing sound.
- Premium feature keys are string constants backed by in-memory entitlements until billing is introduced.
- Stage channels reuse `GUILD_VOICE` channel records with stage-specific session state for this slice.
