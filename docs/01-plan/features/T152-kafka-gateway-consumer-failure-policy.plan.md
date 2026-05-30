# T152 Kafka Gateway Consumer Failure Policy Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T152 Kafka Gateway Consumer Failure Policy

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Kafka Gateway consumer handling silently ignored malformed messages and allowed listener failures to break consumption. |
| Solution | Add an explicit skip-and-dead-letter policy for malformed envelopes, invalid envelopes, and listener failures. |
| Operator Effect | Poison Gateway events become visible in a dedicated dead-letter topic without crashing the consumer path. |
| Core Value | Kafka-backed realtime fanout is safer under malformed input and listener exceptions. |

## Scope

- Add dead-letter publication to `<topic>.dead-letter`.
- Dead-letter malformed JSON without notifying listeners.
- Dead-letter invalid envelopes that cannot become `GatewayBusEvent`.
- Dead-letter listener failures without including raw payloads or exception text.
- Keep same-node message suppression unchanged.
- Add focused unit tests for malformed messages and listener failure.

## Out of Scope

- A full retry/backoff container configuration.
- DLQ drain tooling and alerting.
- Durable metrics for dead-letter counts.

## Success Criteria

- RED tests fail before implementation and pass after implementation.
- Dead-letter payloads do not contain raw Kafka payload content, tokens, signed URLs, or listener exception text.
- Related Gateway event bus tests and checkstyle pass.
