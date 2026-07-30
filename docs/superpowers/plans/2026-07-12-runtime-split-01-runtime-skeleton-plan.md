# Runtime Split: Four-Service Skeleton Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Start four independently deployable services behind a configuration-only Gateway without moving product behavior yet.

**Architecture:** Create four Spring Boot entry points and service-specific dependency sets. The Gateway is local/prod proxy configuration, not a Gradle/Spring module. A shared Postgres cluster may be used initially, but identity, message, and community receive separate logical databases or schemas; websocket-service receives none.

**Tech Stack:** Java 21, Spring Boot, Gradle, Docker Compose, PostgreSQL, Redis/Kafka, JUnit 5.

---

## File Structure

- Modify: `settings.gradle.kts` — add four boot runtime projects and retire `:backend:boot` only in Plan 06.
- Create: `backend/services/identity/build.gradle.kts`, `backend/services/message/build.gradle.kts`, `backend/services/websocket/build.gradle.kts`, `backend/services/community/build.gradle.kts` — service runtime dependency boundaries.
- Create: `backend/services/{identity,message,websocket,community}/src/main/java/.../*Application.java` — one `@SpringBootApplication` per service.
- Create: `backend/services/{identity,message,websocket,community}/src/main/resources/application.yml` — service name, port, health exposure, own datasource policy.
- Modify: `infra/docker/docker-compose.yml` — four local runtime services and their health checks.
- Create: `infra/gateway/` configuration — path routing only; use the selected deployment proxy format, not Java.

### Task 1: Add empty runtime projects — 0.75 MM

- [ ] Add Gradle projects `:backend:services:identity`, `:backend:services:message`, `:backend:services:websocket`, and `:backend:services:community`.
- [ ] Give each project only Spring Boot web/actuator plus the existing domain modules it needs; do not copy all `backend:boot` dependencies.
- [ ] Add one application class per service with an explicit application name (`identity-service`, `message-service`, `websocket-service`, `community-service`).
- [ ] Add a JUnit smoke test per project that starts only its application context and asserts `/actuator/health` is available.
- [ ] Run each focused service test for RED before adding the application entry point, then GREEN after it starts.

### Task 2: Add configuration-only routing — 0.5 MM

- [ ] Define `/auth` to identity-service, `/messages` and `/channels` to message-service, `/ws` to websocket-service, and `/community` to community-service.
- [ ] Configure only upstream base URLs, health checks, timeouts, TLS/proxy headers, and load-balancing targets.
- [ ] Add a contract check that rejects JWT parsing, Java source, database credentials, or event-bus configuration under `infra/gateway`.

### Task 3: Wire local runtime — 1 MM

- [ ] Add Compose services with unique ports and `depends_on` health conditions.
- [ ] Give identity/message/community separate `SPRING_DATASOURCE_URL` values; ensure websocket-service has no datasource configuration.
- [ ] Add `/actuator/health` smoke checks through the Gateway path and direct service ports.
- [ ] Run `docker compose -f infra/docker/docker-compose.yml config` and a four-service startup smoke.

### Task 4: Add cross-service observability baseline — 0.75 MM

- [ ] Configure service name, request ID, and trace-context propagation in each runtime.
- [ ] Preserve trace/span IDs in log bodies; do not use them as Loki labels.
- [ ] Add a smoke test that follows one request through Gateway and one service log without exposing credentials or tokens.

**Exit gate:** all four services start independently; Gateway routes paths only; websocket-service has no datasource; all health checks pass.

