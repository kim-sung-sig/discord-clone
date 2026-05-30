# T177 Kafka Gateway DLQ Workflow Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T177 Kafka Gateway DLQ retention, alert, and replay workflow

## Design

The DLQ runbook defines a metadata-only operational workflow for:

| Area | Policy |
| --- | --- |
| Topic | `<topic-prefix>.gateway.events.dead-letter`, default `discord.gateway.events.dead-letter`. |
| Retention | 168 hours by default. |
| Alert threshold | In production, page when DLQ count is greater than 0. |
| Replay | Requires `SECURITY_ADMIN` or incident role, change ticket approval, fixed/root-caused defect, and a sanitized reconstructed envelope. |
| Discard | Use for malformed, unsafe, duplicate, or stale records; leave records to expire unless targeted deletion is approved. |
| Hold | Requires owner and expiration date. |

## Configuration

`application-kafka.yml` and `.env.example` now expose:

- `DISCORD_KAFKA_GATEWAY_DLQ_RETENTION_HOURS=168`
- `DISCORD_KAFKA_GATEWAY_DLQ_ALERT_THRESHOLD=1`

These values document the operational defaults even when the broker itself owns topic retention configuration.

## Security Review

- The runbook forbids copying raw Kafka payloads.
- Replay must not include tokens, signed URLs, cookies, authorization headers, or private message bodies.
- DLQ metadata uses `messageSha256Prefix` for correlation instead of payload disclosure.
