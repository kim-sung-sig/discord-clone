# Redis Gateway DLQ Runbook

Date: 2026-05-21
Scope: Triage, alerting, replay decision, and discard workflow for Redis Gateway dead-letter records.

## Purpose

Redis Gateway stream failures are written to a metadata-only Redis Stream:

```text
gateway:dead-letter
```

Runtime policy value:

```text
DISCORD_GATEWAY_REDIS_DLQ_STREAM=gateway:dead-letter
```

The stream stores reason, handling node, source node, event ID, event type, original stream key, Redis record ID,
timestamp, message size, and `messageSha256Prefix`. It must not contain raw Gateway payloads.

The DLQ stream is trimmed with the same bounded stream length policy used by Gateway event streams:

```text
DISCORD_GATEWAY_REDIS_STREAM_MAX_LENGTH=10000
```

## Reasons

| Reason | Meaning | Default action |
| --- | --- | --- |
| `MALFORMED_RECORD` | A Redis Stream record could not be decoded as a valid Gateway event. | Treat as poison input and discard after review. |
| `LISTENER_FAILURE` | A local listener threw while handling a decoded Redis Gateway event. | Investigate service logs and replay only after the listener defect is fixed. |

## Alert Threshold

Runtime policy value:

```text
DISCORD_GATEWAY_REDIS_DLQ_ALERT_THRESHOLD=1
```

Alert threshold: page when the DLQ count is greater than 0 in production, because any Redis Gateway DLQ event means a
realtime event was not delivered normally. In staging, create a ticket instead of paging unless the same reason repeats
after a deploy.

Suggested inspection command:

```bash
redis-cli XREVRANGE gateway:dead-letter + - COUNT 20
```

Use `REDISCLI_AUTH` or your managed Redis secret mechanism. Do not pass Redis passwords through command arguments.

## Triage

1. Open or link a change ticket.
2. Confirm the operator has `SECURITY_ADMIN` or an equivalent production incident role.
3. Record the stream, reason, `messageSha256Prefix`, event ID if present, source node, handling node, first observed time,
   and count.
4. Check deploy history, Redis health, and Gateway service logs for the same time window.
5. Classify the outcome as discard, replay, or hold.

## Replay

Replay is allowed only when all conditions are true:

- A change ticket approves replay.
- The parser/listener defect has been fixed or ruled out.
- The original event is known to be safe and idempotent for clients.
- The operator can reconstruct a sanitized Gateway event without adding secrets, raw tokens, signed URLs, private message
  content, Authorization headers, or cookies.

Never replay directly from a dead-letter record. DLQ records intentionally do not contain raw payloads.

## Discard

Discard when the record is malformed, unsafe to reconstruct, duplicated by a later valid event, or no longer relevant to
active clients.

For discard:

1. Add the reason to the change ticket.
2. Record the `messageSha256Prefix` and count.
3. Leave the record to expire by the Redis retention policy unless an approved targeted delete process exists.

## Security Rules

- Do not copy raw Redis stream payloads.
- Do not include access tokens, refresh tokens, signed URLs, Authorization headers, cookies, or private message bodies in
  replay material.
- Do not lower the production alert threshold to disable alerts.
- Do not replay `LISTENER_FAILURE` events until the listener issue is fixed.
- Do not bypass `SECURITY_ADMIN` or incident-role review for replay.
