# Runtime Persistence Migration Design

## Goal

Replace process-local production state with PostgreSQL for durable business data and Redis for distributed volatile data. Keep ordinary unit tests infrastructure-free by mocking ports; run PostgreSQL/Redis contract tests only through explicit opt-in gates.

## Runtime Rules

- No `InMemory*` bean may be registered from `backend/boot/src/main`.
- PostgreSQL owns durable aggregate state and is selected only by the `postgres` profile.
- Redis owns distributed volatile state such as rate limiting, presence TTL, gateway sessions, and event fanout.
- A runtime profile without the required backing service must fail during startup. It must never silently fall back to process-local state.
- `InMemory*` code may remain only as a test double outside production Spring configuration until its owning domain is fully removed.

## Domain Completion Gate

Each domain is migrated as one vertical slice:

1. Introduce or reuse a narrow domain service port.
2. Make the controller depend on that port rather than an `InMemory*` concrete type.
3. Register only the PostgreSQL or Redis adapter in its runtime profile.
4. Add a failing focused runtime-selection test before the wiring change.
5. Verify persistence across a new adapter instance or Spring context where applicable.
6. Keep controller unit tests infrastructure-free through mocked ports.
7. Remove the domain's production `InMemory*` configuration and verify no boot-main reference remains.

The domain is incomplete if its adapter is only exercised directly by a test while its controller still receives an in-memory implementation.

## First Slice: Expression

`expression` is the first runtime conversion because its `JdbcExpressionService` and PostgreSQL schema already exist, but its controller and configuration still depend on `InMemoryExpressionService`.

### Change

- Add an `ExpressionService` port in the expression module for emoji, sticker, and reaction operations.
- Have `JdbcExpressionService` implement the port.
- Make `ExpressionController` depend on the port.
- Register the JDBC implementation only under `postgres`; remove the production in-memory bean.
- Replace controller test construction with a mock of `ExpressionService`.

### Success Criteria

- Under `postgres`, the injected expression service is `JdbcExpressionService`.
- Emoji, sticker, and reaction state survive a fresh adapter/context read from PostgreSQL.
- The default runtime cannot construct an expression controller without its required persistent adapter.
- Expression controller unit tests run with a mocked `ExpressionService` and no PostgreSQL dependency.
- `rg "InMemoryExpressionService" backend/boot/src/main` returns no expression runtime references.

### Failure Criteria

- A production profile creates or injects `InMemoryExpressionService`.
- Missing PostgreSQL configuration still permits expression runtime startup through fallback state.
- A fresh JDBC adapter cannot read state written by another adapter instance.
- PostgreSQL contract tests become part of the default test command.

## Migration Order

1. expression
2. thread
3. event
4. notification
5. message
6. guild and invite
7. auth
8. social, moderation, storage
9. presence, voice, gateway, and rate limit through Redis
10. experience, bot webhook, and remaining supporting state

Each slice requires focused RED/GREEN evidence, scoped regression tests, `git diff --check`, and a separate commit. Existing unrelated dirty files are never included.
