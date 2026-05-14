# T14 Stage/Soundboard/Premium Skeleton Feedback

작성일: 2026-05-14  
PDCA Phase: Act  
Slice: T14 Stage/Soundboard/Premium Skeleton

## Feedback Items

| ID | Type | Detail | Action |
| --- | --- | --- | --- |
| T14-FB-001 | Limitation | Stage channel is modeled as `GUILD_VOICE` plus session state. | Add explicit stage channel type in a future schema task if product requires separate channel semantics. |
| T14-FB-002 | Limitation | Soundboard play emits a deterministic event but no actual audio or gateway broadcast. | Connect to voice gateway/broadcast layer in a later realtime media task. |
| T14-FB-003 | Limitation | Premium entitlement grant is a test skeleton. | Add billing/provider integration only after entitlement persistence and feature flag policy are designed. |
| T14-FB-004 | Quality | Existing Nuxt/Vue warnings remain during build. | Track separately; not blocking T14 because build succeeds. |

## PDCA Act Decision

- No rework loop required for T14.
- Carry limitations forward as explicit future tasks rather than expanding this skeleton slice.
