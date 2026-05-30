# T144 Central Runtime Resource Profiles Feedback

Date: 2026-05-19
Slice: T144 Central Runtime Resource Profiles

## Improvement Tasks Captured

### T145 Remove Runtime In-Memory Persistence Defaults

For production-like runs, fail closed or require `postgres` instead of silently using in-memory auth, guild, message, invite, and audit stores.

### T146 Kafka Gateway Event Bus Adapter

Add a Kafka-backed `GatewayEventBus` profile for cross-node fanout using the prepared `ms-kafka` broker.

### T147 Docker Compose Resource Topology Alignment

Update `infra/docker/docker-compose.yml` or add a dev override so it matches `postgres-source`, `ms-redis`, and `ms-kafka` host ports.

### T148 Central Redis Smoke Check

Add a local QA smoke that verifies backend Redis profile connectivity to `127.0.0.1:16379` and Nuxt CSP limiter connectivity.

### T149 Reference Docker Compose Drift Check

Add a QA contract that compares this workspace's resource defaults against `C:\git\chat-platform\docker\compose.yml` and reports intentional differences such as `discord` versus `messagesystem`.

## Loop Decision

T144 scored 27/30 and passed the threshold. Continue to T145 if removing runtime in-memory fallbacks is the priority, otherwise return to T122 admin role runbook.
