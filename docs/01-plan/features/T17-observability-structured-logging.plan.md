# T17 Observability/Structured Logging Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T17 Observability/Structured Logging

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | `X-Request-Id`는 응답으로만 반환되고 로그/메트릭과 연결되지 않아 런타임 오류 추적이 어렵다. |
| Solution | API filter에서 request id를 MDC에 넣고, JSON 콘솔 로그와 Micrometer timer/counter로 요청·거절·인증 실패를 관측한다. |
| Function UX Effect | 장애 시 프론트가 보낸 request id와 백엔드 로그/메트릭을 같은 상관키로 연결할 수 있다. |
| Core Value | T23의 실제 프론트/백엔드 플로우 위에 운영 가능한 진단 기반을 만든다. |

## Scope

- Map safe `X-Request-Id` to SLF4J MDC for `/api/**`.
- Clear MDC after every request to prevent cross-request leakage.
- Add structured JSON console log baseline through Logback configuration.
- Record API latency with low-cardinality Micrometer tags.
- Count API rejections and auth login failures without logging tokens, passwords, or message bodies.
- Add tests proving MDC population, MDC clearing, request timer, and auth failure counter behavior.
- Add runtime smoke evidence that a supplied correlation id appears in structured logs.

## Out of Scope

- OpenTelemetry exporter setup.
- Centralized log storage.
- Production log shipping topology.
- Rate limiting and abuse controls; that remains T19.
- Full user/guild tracing across every service method.

## Success Criteria

- Every API request log can be correlated by request id.
- Sensitive data such as tokens, passwords, and message bodies is not logged.
- Auth failures and forbidden/unauthorized actions are observable through metrics and safe warning logs.
- Tests prove MDC is populated and cleared per request.
- Runtime smoke demonstrates frontend/API correlation id propagation into backend logs.

## Failure Criteria

- Request id remains only a response header and is absent from MDC/logs.
- Token, password, authorization header, or message content appears in logs.
- Metrics include high-cardinality raw UUID paths as labels.
- MDC leaks across requests.
