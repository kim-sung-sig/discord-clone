# T19 Deployment Security/Abuse Controls Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T19 Deployment Security/Abuse Controls

## Verification Summary

| Area | Command | Result |
| --- | --- | --- |
| Backend RED | `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks` before limiter implementation | FAIL, auth/message/invite/gateway rate limit assertions failed as expected |
| Backend targeted GREEN | `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Frontend RED | `npm run test -- --run tests/components/security-headers.test.ts` before middleware implementation | FAIL, missing `server/utils/security-headers` module |
| Frontend targeted GREEN | `npm run test -- --run tests/components/security-headers.test.ts` | PASS, 1 test |
| Backend controller regression | `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthControllerTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Attachment regression | `.\gradlew.bat :backend:boot:test --tests com.example.discord.storage.AttachmentControllerTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Frontend full unit suite | `npm run test -- --run` | PASS, 5 files / 40 tests |
| Frontend production build | `npm run build` | PASS, Nuxt build complete |
| Backend full suite | `.\gradlew.bat test` | PASS, BUILD SUCCESSFUL |

## Success Criteria Check

- API and HTML responses both have documented security headers: PASS. API headers remain in `OperationalHardeningFilter`; HTML headers are added through Nuxt Nitro middleware.
- Brute-force auth attempts are rate-limited before lockout abuse: PASS. Auth login is limited to 2/min by client IP, before the existing third-failure account lockout path.
- Message spam is throttled: PASS. `POST /api/channels/{uuid}/messages` is limited by hashed bearer subject or client IP.
- Invite accept bursts are throttled: PASS. Invite codes normalize to `/api/invites/{token}/accept`, preventing code-shape bypass.
- Gateway identify bursts are throttled: PASS. `POST /api/gateway/identify` is limited by hashed bearer subject or client IP.
- Rate limit tests cover user/IP/key dimensions: PASS through IP auth/gateway tests, bearer-subject message/invite tests, and normalized path assertions.
- Production path is not hard-wired to process memory: PASS at architecture boundary. `RateLimitStore` is a replaceable port, with a Redis store command model documented separately.

## Design Match

- `OperationalHardeningFilter` remains the common API edge for request id, observability, hardening headers, and rate-limit rejection.
- `RateLimitStore`, `RateLimitKey`, `RateLimitPolicy`, and `RateLimitDecision` isolate limiter storage from filter policy.
- `InMemoryRateLimitStore` is used for local deterministic tests.
- `T19-redis-rate-limit-store.design.md` documents Redis atomic `INCR`/`PEXPIRE` production parity semantics.
- Nuxt security headers are defined as a pure map in `server/utils/security-headers.ts` and applied by `server/middleware/security-headers.ts`.

## Findings And Fixes

- Existing auth controller tests initially failed under the new limiter because all MockMvc login attempts shared the same synthetic client IP. Fixed by assigning explicit `X-Forwarded-For` values so lockout tests continue to verify lockout behavior rather than IP throttling.
- Existing attachment orphan cleanup test was timing-sensitive because the API configuration uses a one-nanosecond orphan TTL with a system clock that may have millisecond precision. Fixed by waiting 5ms before cleanup in the API-level test.
- Auth limiter policy changed from the initial 5/min design draft to 2/min because the existing login lockout threshold is 3 failed attempts; limiting at 5 would not satisfy "before lockout abuse."

## Residual Risks

- The default store is in-memory and suitable for local/dev only; production horizontal scaling must wire a Redis implementation of the documented `RateLimitStore` contract.
- Bearer-token hashing is a safe subject approximation but does not collapse multiple active tokens for the same user until the API edge has authenticated principal context.
- Nuxt CSP allows inline styles because current component styling and Nuxt output require it; this should be tightened if the frontend moves to nonce/hash-based CSP.
