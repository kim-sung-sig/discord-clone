# T140 Backend Auth Check Probe Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T140 Backend Auth Check Probe

## Findings

- T120 exposed guard configuration but explicitly left authenticated backend self-tests out of scope.
- A lightweight unauthenticated/dummy-token probe gives useful reachability evidence without requiring a real admin credential.
- Returning raw errors or URLs would risk leaking internal topology or credentials embedded in a misconfigured URL, so the payload must stay minimal.

## Risk Review

| Risk | Control |
| --- | --- |
| Backend URL or token leakage | Do not include URL, request headers, body, or error text in the probe result. |
| False negative on auth-required endpoint | Treat `401` and `403` as reachable. |
| UI accepts malformed probe payload | Extend the guard health type guard. |
| Health endpoint latency | Probe is a single server-side GET; timeout/alerting is deferred. |

## Remaining Gaps

- Probe timeout tuning and alert thresholds are not yet configurable.
