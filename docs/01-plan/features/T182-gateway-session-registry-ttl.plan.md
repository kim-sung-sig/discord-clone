# T182 Gateway Session Registry TTL Plan

Date: 2026-05-21

## Goal

Prevent Redis-backed Gateway session registry metadata from accumulating indefinitely while preserving cross-node RESUME
support.

## Scope

- Replace hash-field storage with per-session Redis keys because hash fields cannot have independent TTLs.
- Keep an index set for session enumeration.
- Refresh session TTL on every save/heartbeat/update.
- Prune expired or malformed index entries during lookup/enumeration.
- Add real central Redis smoke coverage.

## Acceptance

- RED first: tests fail because the Redis session registry lacks TTL-aware per-session storage.
- Session metadata remains secret-safe.
- `DISCORD_GATEWAY_SESSION_TTL_SECONDS=86400` is documented and configurable.
- Central Redis smoke verifies TTL and stale index cleanup with real Redis.
