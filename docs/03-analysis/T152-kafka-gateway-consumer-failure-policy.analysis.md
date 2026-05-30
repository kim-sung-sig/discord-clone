# T152 Kafka Gateway Consumer Failure Policy Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T152 Kafka Gateway Consumer Failure Policy

## Findings

- `KafkaGatewayEventBus.decode` returned empty for malformed payloads, but there was no operational evidence.
- `handleMessage` called listeners directly, so a listener exception could escape the consumer method.
- The existing Kafka topic naming already provides a stable base for a derived dead-letter topic.
- A full Kafka retry/DLQ container setup would be larger than needed for this slice and could reprocess poison messages repeatedly without a clear drain path.

## Risk Review

| Risk | Control |
| --- | --- |
| Raw payload leaks into DLQ | Store only metadata and a hash prefix. |
| Listener exception leaks secrets | Omit exception class/message from dead-letter payload. |
| Poison message kills consumer path | Catch malformed/invalid/listener failures and skip after dead-lettering. |
| Same-node publish creates duplicate DLQ noise | Preserve same-node ignore behavior. |

## Remaining Gaps

- DLQ retention, alert thresholds, and operator drain/replay workflow are not implemented.
