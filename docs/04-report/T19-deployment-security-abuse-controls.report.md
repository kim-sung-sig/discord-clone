# T19 Deployment Security/Abuse Controls Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T19 Deployment Security/Abuse Controls

## Completed

- Added a backend `RateLimitStore` port with local `InMemoryRateLimitStore`.
- Added API rate-limit policies for auth login, invite accept, message create, and gateway identify.
- Added stable subject extraction by client IP or SHA-256 hashed bearer token.
- Added normalized path matching so UUIDs and invite codes cannot bypass limiter buckets.
- Added safe 429 JSON response with `Retry-After`, `X-RateLimit-Limit`, and `X-RateLimit-Remaining`.
- Added Nuxt HTML security headers through Nitro middleware.
- Added frontend unit coverage for CSP, frame, content-type, referrer, and permissions headers.
- Added Redis production parity design for distributed rate-limit storage.
- Stabilized backend controller tests impacted by the new limiter and existing orphan cleanup timing.

## Commits

- `9da6877 docs: plan T19 deployment security abuse controls`
- `0a71fb2 feat: add api abuse rate limits`
- `8d3470e feat: add nuxt html security headers`
- `d97a44b test: isolate rate limits in backend controller tests`

## QA Evidence

- RED backend: `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks`: FAIL before implementation at new rate limit assertions
- GREEN backend targeted: `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks`: PASS
- RED frontend: `npm run test -- --run tests/components/security-headers.test.ts`: FAIL before implementation due missing module
- GREEN frontend targeted: `npm run test -- --run tests/components/security-headers.test.ts`: PASS
- Regression targeted: `.\gradlew.bat :backend:boot:test --tests com.example.discord.auth.AuthControllerTest --rerun-tasks`: PASS
- Regression targeted: `.\gradlew.bat :backend:boot:test --tests com.example.discord.storage.AttachmentControllerTest --rerun-tasks`: PASS
- Frontend full: `npm run test -- --run`: PASS, 40 tests
- Frontend build: `npm run build`: PASS
- Backend full: `.\gradlew.bat test`: PASS

## Outcome

T19 meets the deployment security and abuse-control baseline. API and HTML surfaces now both carry security headers, high-risk write/auth endpoints are throttled with safe 429 responses, and the limiter has a production-ready Redis store contract instead of being coupled directly to process memory.

## Next Task Candidate

Proceed to the recommended next item: `T18 Realtime Media/Gateway Broadcast Integration`.
