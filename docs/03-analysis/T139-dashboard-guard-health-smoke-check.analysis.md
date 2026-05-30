# T139 Dashboard Guard Health Smoke Check Analysis

Date: 2026-05-20
PDCA Phase: Check
Slice: T139 Dashboard Guard Health Smoke Check

## Findings

| Finding | Result |
| --- | --- |
| T120 endpoint returns HTTP 503 when production guard is fail-closed | Smoke treats non-200 as deployment-blocking. |
| CI needs a passing production guard configuration | The CI job uses a dummy `NUXT_SECURITY_DASHBOARD_TOKEN` to prove ready state without real secrets. |
| Windows PowerShell 5 lacks the direct `[System.Net.Http.HttpResponseMessage]` type check used in the first draft | Error response body parsing now uses reflective method/property checks. |
| Nuxt build warnings can appear on native stderr | Build stdout/stderr are written to artifacts instead of noisy pipeline output. |

## Security Review

The smoke uses environment variables for guard configuration and validates that the response body does not echo the configured token. Failure artifacts include logs and guard health JSON, but not the token value.

## Residual Risk

- Backend auth-check reachability is still not probed; tracked as T140.
- Production secret storage and rotation remain operational policy work; related to T129.
