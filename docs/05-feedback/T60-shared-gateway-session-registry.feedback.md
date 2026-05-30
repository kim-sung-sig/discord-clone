# T60 Shared Gateway Session Registry And Cross-node RESUME Feedback

Date: 2026-05-21

## Captured Improvements

| Task | Priority | Note |
| --- | --- | --- |
| T182 Gateway session registry TTL and stale cleanup | P2 | Redis registry entries are now shared and secret-safe, but need expiry/pruning policy before long-running production use. |

## Security Note

Do not add bearer tokens, LiveKit JWTs, request headers, or raw client connection metadata to the shared Gateway session registry.
