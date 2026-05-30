# Kafka Gateway DLQ Runbook

Date: 2026-05-21
Scope: Triage, retention, alerting, replay, and discard workflow for Kafka Gateway dead-letter records.

## Purpose

Kafka Gateway consumer failures are written to the dead-letter topic derived from the Gateway topic:

```text
discord.gateway.events.dead-letter
```

The topic stores metadata only. A dead-letter record may include `messageSha256Prefix`, event ID, event type, source node, handling node, reason, timestamp, and message size. It must not contain raw Gateway payloads.

## Reasons

| Reason | Meaning | Default action |
| --- | --- | --- |
| `MALFORMED_MESSAGE` | The Kafka value could not be parsed as a Gateway envelope. | Treat as poison input and discard after review. |
| `INVALID_ENVELOPE` | The envelope parsed but could not become a valid `GatewayBusEvent`. | Compare hash prefix with deploy/change windows, then discard unless a known serializer bug is confirmed. |
| `LISTENER_FAILURE` | A local listener threw while handling a remote event. | Investigate service logs and replay only after the listener defect is fixed. |

## Retention

Default retention is 168 hours. Keep DLQ data long enough for weekly operations review, but short enough to avoid building a shadow event archive.

Runtime policy value:

```text
DISCORD_KAFKA_GATEWAY_DLQ_RETENTION_HOURS=168
```

If the broker topic retention is managed outside the application, configure the same 168 hours on `discord.gateway.events.dead-letter`. Do not extend retention without an approved incident or compliance reason.

## Alert Threshold

Runtime policy value:

```text
DISCORD_KAFKA_GATEWAY_DLQ_ALERT_THRESHOLD=1
```

Alert threshold: page when the DLQ count is greater than 0 in production, because any Gateway DLQ event means a realtime event was not delivered normally. In staging, create a ticket instead of paging unless the same reason repeats after a deploy.

Runtime metric:

```text
discord.gateway.kafka.dlq.records{reason="<reason>"}
```

Alert routing should sum this metric across Gateway instances over the active alert window and compare it to `DISCORD_KAFKA_GATEWAY_DLQ_ALERT_THRESHOLD`.

Suggested inspection command:

```bash
kafka-console-consumer \
  --bootstrap-server <bootstrap-server> \
  --topic discord.gateway.events.dead-letter \
  --from-beginning \
  --timeout-ms 10000
```

Review only the metadata fields. Do not copy raw Kafka payloads into tickets, chat, or personal notes.

## Triage

1. Open or link a change ticket.
2. Confirm the operator has `SECURITY_ADMIN` or an equivalent production incident role.
3. Record the topic, reason, `messageSha256Prefix`, event ID if present, source node, handling node, first observed time, and count.
4. Check deploy history, broker health, and Gateway service logs for the same time window.
5. Classify the outcome as discard, replay, or hold.

## Replay

Replay is allowed only when all conditions are true:

- A change ticket approves replay.
- The underlying parser/listener defect has been fixed or ruled out.
- The original event is known to be safe and idempotent for clients.
- The operator can reconstruct a sanitized Gateway envelope without adding secrets, raw tokens, signed URLs, or private message content.

Replay command shape:

```bash
kafka-console-producer \
  --bootstrap-server <bootstrap-server> \
  --topic discord.gateway.events
```

Paste only a reviewed, sanitized envelope. Never replay directly from a dead-letter record, because DLQ records intentionally do not contain raw payloads.

## Discard

Discard when the record is malformed, unsafe to reconstruct, duplicated by a later valid event, or no longer relevant to active clients.

For discard:

1. Add the reason to the change ticket.
2. Record the `messageSha256Prefix` and count.
3. Leave the record to expire by retention unless the broker supports an approved targeted delete process.

## Hold

Hold only for active incidents or legal/compliance requests. A hold must name an owner and an expiration date. If retention must exceed 168 hours, document the approved duration and why normal expiry is insufficient.

## Security Rules

- Do not copy raw Kafka payloads.
- Do not include access tokens, refresh tokens, signed URLs, Authorization headers, cookies, or private message bodies in replay envelopes.
- Do not lower the alert threshold in production by disabling alerts.
- Do not replay `LISTENER_FAILURE` events until the listener issue is fixed.
- Do not bypass `SECURITY_ADMIN` or incident-role review for replay.
