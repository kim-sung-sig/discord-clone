# T182 Gateway Session Registry TTL Design

Date: 2026-05-21

## Design

Redis hash fields cannot expire independently, so the registry now stores each session as its own key:

```text
gateway:sessions
gateway:sessions:<sessionId>
```

`gateway:sessions` is an index set. Each `gateway:sessions:<sessionId>` value has a TTL. `save` writes the session key,
adds the ID to the index, and refreshes both TTLs.

Runtime setting:

```text
DISCORD_GATEWAY_SESSION_TTL_SECONDS=86400
```

## Cleanup

`find` and `sessions` prune stale state:

- missing session key: remove ID from the index;
- malformed session JSON: remove ID and delete the session key;
- valid session JSON: return normal `GatewaySession` metadata.

## Security

Stored fields remain limited to session ID, user ID, guild IDs, last acknowledgement timestamp, closed flag, and last
delivered sequence. Tokens, LiveKit JWTs, request headers, cookies, passwords, signed URLs, and message payload bodies are
not stored.
