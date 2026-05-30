# T107 CSP Telemetry Trend Chart Plan

Date: 2026-05-21
PDCA Phase: Plan
Slice: T107 CSP telemetry trend chart

## Executive Summary

| View | Content |
| --- | --- |
| Problem | Operators can see current CSP totals and recent reports, but cannot scan whether violations are rising or clearing over time. |
| Solution | Add a bounded, secret-safe six-hour trend summary to the dashboard payload and `/security` UI. |
| Operator Effect | Operators can quickly correlate current CSP alerts with recent report volume. |
| Core Value | CSP telemetry becomes more actionable without exposing raw browser report data. |

## Scope

- Add hourly trend buckets to `buildCspTelemetryDashboard`.
- Default to six hourly buckets and bound trend source reads.
- Render a compact trend chart in `/security`.
- Add payload and UI tests proving the trend is aggregate-only.

## Out of Scope

- Long-term time-series storage.
- Directive-specific trend stacking.
- Exporting trend data.

## Success Criteria

- RED tests fail before `trend` payload/UI exists.
- Dashboard trend includes zero buckets for empty hours.
- UI does not expose raw URLs, tokens, IPs, or report bodies.
- Focused, related, full web tests and Nuxt build pass.
