# T100 CSP Telemetry Alert Threshold Plan

## Objective

Compute security alert state from CSP telemetry so operators can identify violation spikes instead of manually reading raw totals.

## Current State

- Dashboard exposes accepted CSP report totals and top directives.
- T103 exposes rate-limited report totals.
- No alert state is computed yet.

## Scope

1. Add a CSP alert threshold evaluator.
2. Support total report threshold.
3. Support per-directive report threshold.
4. Include alert state in the dashboard API payload.
5. Configure thresholds through Nuxt environment variables.

## Acceptance Criteria

- Dashboard alert is inactive when thresholds are not exceeded.
- Dashboard alert becomes active when total reports meet/exceed the configured threshold.
- Dashboard alert becomes active when any directive count meets/exceeds the configured threshold.
- Alert payload does not include raw CSP report data.
- Focused tests, full web tests, build, and whitespace checks pass.

