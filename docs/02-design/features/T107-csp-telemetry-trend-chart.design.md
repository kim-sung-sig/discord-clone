# T107 CSP Telemetry Trend Chart Design

Date: 2026-05-21
PDCA Phase: Design
Slice: T107 CSP telemetry trend chart

## Design

The dashboard payload now includes:

```json
{
  "trend": {
    "windowHours": 6,
    "buckets": [
      { "bucketStart": "2026-05-19T00:00:00.000Z", "total": 3 }
    ]
  }
}
```

Buckets are aligned to UTC hours using the dashboard observation time. Missing hours are represented with `total: 0`.

## UI

`/security` renders a compact six-bar chart with:

- bucket total
- hour label
- proportional bar height

The panel is aggregate-only and does not list request IDs, report bodies, document URLs, blocked URLs, user agents, IPs, or subjects.

## Security Review

- Trend data is derived from already sanitized stored telemetry.
- The response includes only `bucketStart` and aggregate `total`.
- The UI test explicitly checks that secret-like URL/IP values are not rendered.
