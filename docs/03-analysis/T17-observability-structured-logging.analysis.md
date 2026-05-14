# T17 Observability/Structured Logging Analysis

작성일: 2026-05-14  
PDCA Phase: Check  
Slice: T17 Observability/Structured Logging

## Verification Summary

| Area | Command | Result |
| --- | --- | --- |
| TDD red | `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks` before implementation | FAIL, MDC and metrics assertions failed as expected |
| MDC/metrics targeted | `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks` | PASS, BUILD SUCCESSFUL |
| Runtime structured log smoke | `powershell -NoProfile -ExecutionPolicy Bypass -File qa\observability-smoke.ps1 -BaseUrl http://127.0.0.1:8080 -RequestId t17-runtime-correlation-2 -LogFile C:\tmp\discord-t17-bootrun.out.log` | PASS, `OBSERVABILITY_SMOKE_PASS` |
| Backend full suite | `.\gradlew.bat test` | PASS, BUILD SUCCESSFUL |

## Success Criteria Check

- Every API request log can be correlated by request id: PASS; runtime JSON log contained `request_id:"t17-runtime-correlation-2"` for `/api/premium/catalog`.
- Unsafe or sensitive data is not logged: PASS for implemented scope; filter logs method, normalized path, status, and message only, while smoke scans for password and bearer token patterns.
- Auth failures are observable: PASS via `discord.auth.failures` counter for invalid credentials and login lock outcomes.
- Forbidden/unauthorized actions are observable: PASS via `discord.api.rejections` counter and warning log for 401/403/423 statuses.
- Tests prove MDC is populated and cleared per request: PASS via MockMvc probe and post-request `MDC.get(...) == null` assertions.
- Metrics avoid raw UUID path cardinality: PASS via `/api/observability/mdc/{uuid}` normalization assertion.

## Design Match

- `OperationalHardeningFilter` remains the single `/api/**` request id boundary.
- MDC keys are `request_id`, `http_method`, `http_path`, and `http_status`.
- Micrometer metrics use low-cardinality method/path/status/outcome tags.
- Logback JSON console baseline includes MDC fields and does not include request headers or bodies.
- Runtime smoke verifies both response header echo and structured log correlation.

## Findings And Fixes

- RED stage confirmed the new tests were meaningful: MDC values were empty and metrics did not exist before implementation.
- Initial smoke execution hit a Windows PowerShell `Invoke-WebRequest` NullReference parser issue; fixed by using `-UseBasicParsing`.
- Startup logs are also emitted as JSON with empty MDC fields, which is expected because they occur outside an API request.

## Residual Risks

- JSON logging uses a Logback pattern baseline rather than a dedicated JSON encoder dependency.
- Metrics are available in-process through Micrometer but still require production scrape/export configuration.
- Safe user/guild identifiers are not yet attached globally; adding them requires stable controller/service context boundaries.
