---
slug: T60-shared-gateway-session-registry
ticket: T60
phase: feedback
hub: "[[T60-shared-gateway-session-registry]]"
---
> 🧭 [[T60-shared-gateway-session-registry]] · PDCA: [[T60-shared-gateway-session-registry.plan]] → [[T60-shared-gateway-session-registry.design]] → [[T60-shared-gateway-session-registry.analysis]] → [[T60-shared-gateway-session-registry.report]] → **feedback**

# T60 Shared Gateway Session Registry And Cross-node RESUME Feedback

Date: 2026-05-21

## Captured Improvements

| Task | Priority | Note |
| --- | --- | --- |
| T182 Gateway session registry TTL and stale cleanup | P2 | Redis registry entries are now shared and secret-safe, but need expiry/pruning policy before long-running production use. |

## Security Note

Do not add bearer tokens, LiveKit JWTs, request headers, or raw client connection metadata to the shared Gateway session registry.
