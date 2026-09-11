---
slug: T183-redis-gateway-source-node-duplicate-suppression
ticket: T183
phase: design
hub: "[[T183-redis-gateway-source-node-duplicate-suppression]]"
---
> 🧭 [[T183-redis-gateway-source-node-duplicate-suppression]] · PDCA: [[T183-redis-gateway-source-node-duplicate-suppression.plan]] → **design** → [[T183-redis-gateway-source-node-duplicate-suppression.analysis]] → [[T183-redis-gateway-source-node-duplicate-suppression.report]] → [[T183-redis-gateway-source-node-duplicate-suppression.feedback]]

# T183 Redis Gateway Source-node Duplicate Suppression Design

Date: 2026-05-21

## Policy

Redis Gateway records are first decoded as normal. If decoding succeeds and the record `sourceNodeId` equals the current Gateway node id, the bus treats the record as an already locally delivered publish:

- do not call remote listener delivery again
- do acknowledge the Redis stream record
- do not count it as a remote processed event

Malformed own-source records still follow the existing malformed-record DLQ path because suppression happens only after successful decode.

## Security Notes

- The policy does not expose raw payloads, exception messages, tokens, signed URLs, or headers.
- `sourceNodeId` remains routing metadata only. It is not an authorization signal for client input.
- Cross-node records with a different `sourceNodeId` keep the existing listener failure DLQ behavior.
