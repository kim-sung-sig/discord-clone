# T128 CSP Rate-Limit Subject Diagnostics Plan

Date: 2026-05-20
PDCA Phase: Plan
Slice: T128 CSP Rate-Limit Subject Diagnostics

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Operators could configure trusted proxy subject normalization, but could not confirm which source was being used without inspecting raw network data. |
| Solution | Add secret-safe subject diagnostics to the security dashboard. |
| Operator Effect | Operators can confirm proxy trust matching, header presence, and subject derivation source without seeing raw IP addresses. |
| Core Value | Rate-limit behavior becomes auditable without expanding sensitive telemetry exposure. |

## Scope

- Add a diagnostics helper beside CSP rate-limit subject normalization.
- Include diagnostics in the guarded CSP telemetry dashboard payload.
- Render diagnostics in `/security`.
- Keep raw IPs, raw forwarded headers, and full hashes out of the payload/UI.

## Out of Scope

- Per-client IP display.
- Historical subject drilldown.
- Rate-limit bypass controls.

## Success Criteria

- Diagnostics identify `remote-address`, `x-forwarded-for`, `x-real-ip`, or `unknown` as the source.
- Diagnostics show trusted proxy configured/matched state and rule count.
- Diagnostics expose only a short subject hash prefix.
- UI renders diagnostics without raw IP exposure.
