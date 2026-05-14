# T17 Observability/Structured Logging Report

작성일: 2026-05-14  
PDCA Phase: Report  
Slice: T17 Observability/Structured Logging

## Completed

- Added request-scoped SLF4J MDC for `/api/**` requests.
- Added MDC cleanup in `finally` to prevent cross-request leakage.
- Added low-cardinality API request latency timer `discord.api.requests`.
- Added rejection counter `discord.api.rejections` for 401/403/423 responses.
- Added auth failure counter `discord.auth.failures` for invalid credentials and login lock outcomes.
- Added safe API outcome logs that include request id, method, normalized path, and status through MDC.
- Added JSON console log baseline through `logback-spring.xml`.
- Added observability smoke script that verifies response/log request id correlation and scans for obvious sensitive leakage.
- Added tests for MDC population, MDC clearing, request metrics, auth failure metrics, and UUID path normalization.

## Commits

- `981f403 docs: plan T17 observability structured logging`
- `0e7504d feat: add api observability context`
- `6815f9c feat: add structured api log smoke`

## QA Evidence

- RED: `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks`: FAIL before implementation at MDC/metrics assertions
- GREEN: `.\gradlew.bat :backend:boot:test --tests com.example.discord.ops.OperationalHardeningFilterTest --rerun-tasks`: PASS
- Runtime: `powershell -NoProfile -ExecutionPolicy Bypass -File qa\observability-smoke.ps1 -BaseUrl http://127.0.0.1:8080 -RequestId t17-runtime-correlation-2 -LogFile C:\tmp\discord-t17-bootrun.out.log`: PASS
- Full backend: `.\gradlew.bat test`: PASS

## Outcome

T17 meets its observability baseline criteria. API logs and metrics now share a request id correlation key, auth and rejection outcomes are visible without logging sensitive request material, and path metrics avoid raw UUID cardinality.

## Next Task Candidate

Proceed to the recommended next item: `T19 Deployment Security/Abuse Controls`.
