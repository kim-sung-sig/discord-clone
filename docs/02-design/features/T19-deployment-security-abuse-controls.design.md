# T19 Deployment Security/Abuse Controls Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T19 Deployment Security/Abuse Controls

## Architecture Decision

Add a narrow abuse-control layer at the same boundary as operational hardening: `OperationalHardeningFilter`. The filter already sees every `/api/**` request before controller execution, has normalized path logic from T17, and can return a consistent 429 response without touching every controller.

Nuxt HTML security headers are handled in `server/middleware/security-headers.ts`. This keeps HTML headers close to the deployment-facing web app instead of pretending the Spring API CSP protects browser-rendered HTML.

## Rate Limiter Boundary

Create `backend/boot/src/main/java/com/example/discord/ops/RateLimitStore.java` as the production parity port:

- `RateLimitDecision consume(RateLimitKey key, RateLimitPolicy policy, Instant now)`
- `RateLimitKey` contains a policy id and a stable subject.
- `RateLimitPolicy` contains a limit and window duration.
- `RateLimitDecision` contains allowed/limit/remaining/retryAfter.

Default implementation:

- `InMemoryRateLimitStore`
- deterministic and test-friendly
- used for local/dev tests

Production parity:

- Document `RedisRateLimitStore` design in `docs/02-design/features/T19-redis-rate-limit-store.design.md`.
- Redis algorithm uses atomic `INCR` plus `PEXPIRE` or a Lua script for first-write TTL.
- Key format: `discord:rate-limit:{policy}:{subject}:{windowEpoch}`.

## Policies

| Policy | Matcher | Subject | Limit |
| --- | --- | --- | --- |
| `auth-login` | `POST /api/auth/login` | client IP | 5 / minute |
| `invite-accept` | `POST /api/invites/{code}/accept` | authenticated user id or client IP | 10 / minute |
| `message-create` | `POST /api/channels/{uuid}/messages` | authenticated user id or client IP | 30 / minute |
| `gateway-identify` | `POST /api/gateway/identify` | authenticated user id or client IP | 10 / minute |

Endpoint matching uses normalized paths, so changing UUIDs or invite codes cannot create a new limiter bucket.

## Authenticated Subject Extraction

The filter must not validate tokens itself because that would duplicate auth semantics and add expensive work to every request. Instead it derives a stable safe subject from the `Authorization` header by hashing the bearer token with SHA-256. This covers user-dimension throttling without logging or storing the token itself. If no bearer token exists, the filter falls back to client IP.

## Error Response Policy

On rejection:

- status `429`
- `Content-Type: application/json`
- body `{ "message": "rate limit exceeded" }`
- headers:
  - `Retry-After`
  - `X-RateLimit-Limit`
  - `X-RateLimit-Remaining`

No request body, auth header, token, invite code, or message content is logged or returned.

## Test Strategy

Backend:

- auth login sixth request is 429 by IP.
- message create is throttled by hashed bearer subject, not raw path UUID.
- invite accept path normalization prevents code-shape bypass.
- gateway identify is throttled by policy id.
- 429 response has safe body and retry headers.

Frontend:

- Nuxt security header middleware exports a pure header map for unit testing.
- E2E or build verification confirms middleware compiles.

## Risks

- In-memory limiter is not distributed; production must wire a Redis store before horizontal scaling.
- Hashing bearer tokens creates stable local subjects but cannot distinguish rotated tokens for the same user until a real authenticated principal is made available at the filter boundary.
- CSP may need adjustment when external assets/CDNs are introduced.
