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

