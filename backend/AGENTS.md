# Backend Engineering Rules

These rules apply to all work under `backend/`.

Also load matching project rule files from `.agents/rules/*.md`; for logging or observability-related backend work, read `.agents/rules/backend-logging.md`.

## Architecture
- Keep domain modules framework-light. Spring MVC, persistence adapters, scheduling, and web concerns belong in `backend/boot`.
- Prefer DDD boundaries: commands and value objects enter domain services; controllers translate HTTP into use cases and response records.
- Event-driven propagation must originate from successful domain state changes. Do not expose public endpoints that let clients forge gateway or integration events.
- Saga/process-manager logic belongs in an application layer or boot orchestration component, not inside controllers.
- Module dependencies must be intentional and acyclic. A module may depend on shared value objects or lower-level domain contracts, but not on boot controllers/configuration.

## Validation
- Use `spring-boot-starter-validation` for HTTP request DTO validation.
- Annotate controller request records with `@Valid` and field constraints such as `@NotBlank`, `@NotNull`, `@Size`, `@Positive`, and `@PositiveOrZero`.
- Keep deeper business invariants inside value objects and domain services so non-HTTP callers are protected too.

## Records And Lombok
- Use Java `record` for immutable request/response DTOs, commands, events, and simple value carriers.
- Use Lombok only when it removes real boilerplate in mutable adapter or persistence classes. Do not add Lombok where records or explicit constructors are clearer.
- Do not use Lombok in domain value objects if generated mutability or hidden behavior makes invariants harder to audit.

## TDD And Coverage
- Write or update failing tests before behavior changes.
- Controller tests must verify auth, validation, error status, and serialization contracts.
- Domain service tests must cover invariants and edge cases without Spring context when possible.
- Coverage targets are behavioral: critical auth, permissions, event propagation, persistence, and gateway/voice flows need regression tests before refactoring.

## Security
- Authenticate mutating APIs and enforce authorization in the application/domain boundary.
- Authenticate read APIs that expose guild topology, channel visibility, presence, voice, gateway, role, audit, or maintenance state. Require membership or a stronger permission before returning resource details.
- Derive acting user identity from bearer tokens. Do not trust `memberId`, `ownerId`, or `userId` from request data except in audited admin/operator flows.
- Channel-scoped APIs must check channel visibility or a stronger channel permission before reading, mutating, or delivering events.
- Internal adapters must require an internal token or profile gate and must not rely on normal user bearer tokens.
- Never log raw access tokens, refresh tokens, passwords, or internal publisher tokens.
- Store reusable token state as hashes where possible and compare internal/webhook shared secrets with constant-time comparison.
- Use explicit error responses for client recovery, but avoid leaking sensitive implementation details.

## Runtime And Scale
- Keep hot reads, event replay, and searches bounded by cursor, limit, retention, or per-resource indexes.
- Keep moderation, audit, alert, report, and webhook audit logs bounded per resource or per service.
- Do not add default-on tests that require local PostgreSQL, Redis, Kafka, or LiveKit. Gate them with an explicit environment variable or provide the dependency in the test harness.
- Treat `X-Forwarded-For` and similar proxy headers as trusted only after validating the immediate peer is a trusted proxy.
- Production profiles must reject known development secrets and resource defaults.
