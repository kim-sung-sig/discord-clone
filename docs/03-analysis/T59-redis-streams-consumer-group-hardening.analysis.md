# T59 Redis Streams Consumer-group Hardening Analysis

Date: 2026-05-21

## RED Evidence

- `.\gradlew.bat :backend:boot:test --tests com.example.discord.gateway.RedisGatewayEventBusTest` failed because the event bus did not accept consumer-group/retention configuration and exposed no metrics.

## GREEN Evidence

- Redis event bus tests passed after adding consumer group reads, pending recovery, ACK, metrics, and trimming.

## Security Review

The implementation keeps payload sanitization at publish time and only exposes aggregate counters. No raw payloads, tokens, signed URLs, or headers are recorded in metrics.

## Remaining Risk

This is still a unit-level adapter hardening pass. Live Redis multi-node consumer-group proof remains tracked by T61.
