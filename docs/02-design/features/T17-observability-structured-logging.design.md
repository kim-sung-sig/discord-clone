# T17 Observability/Structured Logging Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T17 Observability/Structured Logging

## Architecture Decision

Extend the existing API hardening filter into the backend observability boundary. It already owns `/api/**` request id generation and response echo, so it is the safest single point to populate MDC, measure request duration, and emit low-cardinality request outcome logs.

## MDC Contract

MDC keys:

- `request_id`: sanitized request id echoed in `X-Request-Id`.
- `http_method`: request method.
- `http_path`: normalized API path with UUID/numeric segments replaced by placeholders.
- `http_status`: final response status.

The filter sets MDC before dispatch and clears these keys in `finally` after metrics/logging. Tests must prove the key is visible inside the request and absent after the request completes.

## Structured Logging Baseline

Add `logback-spring.xml` for JSON console logs with:

- timestamp
- level
- service
- logger
- thread
- MDC fields
- message

The baseline intentionally avoids logging request/response bodies and headers. This keeps tokens, passwords, and message content out of logs while still preserving correlation and status context.

## Metrics

Use the existing Spring Boot Actuator Micrometer dependency.

- `discord.api.requests`: timer tagged by method, normalized path, and status.
- `discord.api.rejections`: counter tagged by method, normalized path, and status for 401/403/423.
- `discord.auth.failures`: counter tagged by outcome for `/api/auth/login` statuses 401 and 423.

Path normalization prevents per-resource UUID labels from exploding metric cardinality.

## Runtime Evidence

Run Spring Boot locally and call an API with a known request id such as `t17-runtime-correlation`. The JSON log output must include the same `request_id` value, while response headers still echo it.

## Test Strategy

- MockMvc probe endpoint verifies MDC fields are available during request handling.
- A second request verifies MDC does not reuse the prior request id.
- Timer assertions verify successful API calls are recorded.
- Invalid login assertions verify auth failure counters are incremented.
- Existing hardening tests continue to verify request id header generation, sanitization, and local CORS behavior.

## Risks

- Logback JSON pattern is a baseline, not a replacement for a production log encoder/exporter.
- Metrics are process-local until production scrape/export configuration is added.
- Safe user/guild context is intentionally deferred until controller/service boundaries expose stable non-sensitive identifiers consistently.
