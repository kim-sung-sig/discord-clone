# T100 CSP Telemetry Alert Threshold Analysis

## Result

CSP telemetry dashboard payload now includes alert state computed from aggregate report thresholds.

## Behavior

- Total report threshold can activate an alert.
- Per-directive threshold can activate an alert.
- Invalid or empty threshold environment values disable that threshold.
- Alert reasons include only aggregate counts and directive names.

## Configuration

- `NUXT_CSP_ALERT_TOTAL_THRESHOLD`
- `NUXT_CSP_ALERT_DIRECTIVE_THRESHOLD`

## Payload

```json
{
  "alert": {
    "active": true,
    "reasons": [
      "total reports 3 reached threshold 3"
    ]
  }
}
```

