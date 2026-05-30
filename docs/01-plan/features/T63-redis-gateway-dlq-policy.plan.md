# T63 Redis Gateway DLQ Policy Plan

Date: 2026-05-21

## Goal

Add a secret-safe failure policy for Redis Gateway fanout so malformed records and listener failures do not break polling
or leak raw realtime payloads.

## Scope

- Dead-letter malformed Redis stream records.
- Dead-letter listener failures while continuing polling and ACK flow.
- Expose aggregate reason counts and threshold alert state.
- Bound DLQ storage with the existing Redis stream max-length trim.
- Add an operator runbook and CI contract.

## Acceptance

- RED first: Redis Gateway tests fail because `deadLetterMetrics()` and DLQ behavior do not exist.
- DLQ records contain metadata only: reason, node IDs, event ID/type, stream, record ID, size, and hash prefix.
- Raw payload values, access tokens, signed URLs, and exception messages are not written to DLQ records.
- Central Redis smoke still passes.
