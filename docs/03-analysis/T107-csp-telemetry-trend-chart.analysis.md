# T107 CSP Telemetry Trend Chart Analysis

Date: 2026-05-21
PDCA Phase: Analysis
Slice: T107 CSP telemetry trend chart

## Findings

- `buildCspTelemetryDashboard` already has a trusted observation time hook, making deterministic trend bucket tests straightforward.
- The CSP store exposes recent sanitized entries; using bounded recent reads is enough for the six-hour dashboard trend.
- A six-hour default keeps UI density low and avoids long-term analytics scope.

## Risk Review

| Risk | Control |
| --- | --- |
| Trend leaks raw telemetry | Payload includes only hour bucket and count. |
| Recent limit hides trend entries | Trend uses a separate bounded `trendLimit` from the UI recent list. |
| Empty hours disappear | Builder emits zero-count buckets. |
| UI layout shifts with dynamic counts | Bars use fixed grid dimensions and proportional heights. |

## Remaining Gaps

- Long-term trend storage and directive-level trend comparisons are not implemented.
