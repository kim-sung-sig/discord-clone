# T59 Redis Streams Consumer-group Hardening Plan

Date: 2026-05-21

## Goal

Harden Redis-backed Gateway fanout so stream delivery uses consumer groups instead of simple local offsets, recovers pending entries, and applies bounded stream retention.

## Scope

- Convert `RedisGatewayEventBus` polling to consumer-group reads.
- Read pending entries before new entries.
- ACK processed or discarded records.
- Add lightweight stream processing metrics.
- Trim streams to a configurable maximum length after publish.

## Acceptance Criteria

- Redis adapter tests prove consumer-group creation, pending recovery read, ACK, and metrics.
- Publish trims streams to the configured retention length.
- Existing payload sanitizer behavior remains intact.
- No raw tokens or secrets are added to metrics or stream state.
