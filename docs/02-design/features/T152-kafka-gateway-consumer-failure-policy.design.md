# T152 Kafka Gateway Consumer Failure Policy Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T152 Kafka Gateway Consumer Failure Policy

## Design

Kafka consumer failure policy is skip-and-dead-letter:

| Failure | Behavior |
| --- | --- |
| Malformed JSON | Publish a dead-letter record with reason `MALFORMED_MESSAGE` and skip listener delivery. |
| Invalid envelope | Publish reason `INVALID_ENVELOPE` and skip listener delivery. |
| Listener exception | Publish reason `LISTENER_FAILURE` and continue without throwing from `handleMessage`. |
| Same-node message | Ignore as before; no dead-letter. |

Dead-letter records are written to `<gateway-topic>.dead-letter` using the handling node ID as the key.

## Dead-Letter Payload

The payload contains only:

- reason
- handling node ID
- optional source node ID
- optional event ID/type
- received timestamp
- message size
- 12-character SHA-256 message hash prefix

It does not include the raw Kafka message, event payload, listener exception message, access tokens, or signed URLs.

## Security Review

- Raw message content is never copied into the DLQ.
- Exception messages are intentionally omitted because they may contain user data or secrets.
- Hash prefix supports correlation without exposing payload contents.
