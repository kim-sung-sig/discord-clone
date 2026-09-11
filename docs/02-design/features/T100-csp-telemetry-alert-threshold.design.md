---
slug: T100-csp-telemetry-alert-threshold
ticket: T100
phase: design
hub: "[[T100-csp-telemetry-alert-threshold]]"
---
> 🧭 [[T100-csp-telemetry-alert-threshold]] · PDCA: [[T100-csp-telemetry-alert-threshold.plan]] → **design** → [[T100-csp-telemetry-alert-threshold.analysis]] → [[T100-csp-telemetry-alert-threshold.report]] → [[T100-csp-telemetry-alert-threshold.feedback]]

# T100 CSP Telemetry Alert Threshold Design

## Evaluator

Add `csp-alert-threshold.ts` with:

- `evaluateCspTelemetryAlert(summary, options)`
- `createCspAlertThresholdOptions(env)`

## Options

- `totalReportThreshold`
- `directiveReportThreshold`

Environment variables:

- `NUXT_CSP_ALERT_TOTAL_THRESHOLD`
- `NUXT_CSP_ALERT_DIRECTIVE_THRESHOLD`

Empty or invalid values disable that threshold.

## Dashboard Payload

Add:

```json
{
  "alert": {
    "active": true,
    "reasons": [
      "total reports 50 reached threshold 50"
    ]
  }
}
```

The payload contains only aggregate counts and reason strings.

