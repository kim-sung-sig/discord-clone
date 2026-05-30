# T108 CSP Alert Dashboard Banner Design

## UI Contract

The `/security` page reads `CspTelemetryDashboard.alert`.

- `alert.active=true`: render a warning banner above the summary strip.
- `alert.reasons`: render each reason as text in the banner.
- `alert.active=false` or missing alert: render nothing.

## Layout

- Reuse the dashboard page width and card spacing.
- Use a two-column banner on desktop: status label on the left, reasons on the right.
- Collapse to one column on small screens.

## Safety

- Reasons are rendered as plain text from the already sanitized dashboard payload.
- No raw CSP report body, document path, or query string is displayed.
- The banner is an additive UI surface and does not alter dashboard API authorization.
