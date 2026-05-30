# Gateway Session Registry TTL Runbook

Date: 2026-05-21
Scope: Redis-backed Gateway session registry expiry and stale index cleanup.

## Purpose

The Redis Gateway session registry stores resumable session metadata for cross-node Gateway reconnects. Session entries are
stored as per-session keys and indexed by:

```text
gateway:sessions
gateway:sessions:<sessionId>
```

Runtime values:

```text
DISCORD_GATEWAY_SESSION_REGISTRY_KEY=gateway:sessions
DISCORD_GATEWAY_SESSION_TTL_SECONDS=86400
```

save refreshes the session key TTL and the index TTL. sessions() removes index members whose session key has expired
or whose metadata cannot be decoded.

## Security Rules

Session registry values may include session ID, user ID, guild IDs, last acknowledgement timestamp, closed flag, and last
delivered sequence only.

Do not store access tokens, refresh tokens, LiveKit JWTs, Authorization headers, cookies, passwords, signed URLs, raw
request headers, or message payload bodies in the session registry.

## Operations

- Default TTL is 24 hours.
- Lower TTL if the deployment has strict memory pressure or short reconnect windows.
- Raise TTL only with an incident or product reason, because longer TTL increases stale metadata retention.
- If the index contains stale IDs, allow `sessions()` to prune them naturally during Gateway heartbeat timeout scans or
  administrative inspection.
- Do not delete the whole registry key during normal operation unless all active Gateway clients can reconnect cleanly.
