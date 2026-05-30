# T168 Privacy-Reviewed Subject Distribution Summary Plan

Date: 2026-05-30
Status: Implemented

## Goal

Give security operators aggregate visibility into CSP rate-limit subject concentration without exposing raw IP addresses, forwarded headers, subject hashes, or per-subject identifiers.

## Scope

- Extend CSP rate-limit telemetry summaries with a privacy-safe subject distribution.
- Count unique limited subjects and top subject concentration by rank only.
- Expose the distribution through the guarded CSP telemetry dashboard payload.
- Render the summary on `/security` without raw subjects or hash prefixes.
- Add focused payload and dashboard tests for privacy boundaries.

## Out Of Scope

- Raw subject export.
- Hash-prefix or stable pseudonymous subject labels in the aggregate distribution.
- New persistence tables or migrations.
- Production alert thresholds for subject concentration.

## Verification

- `npm test --workspace @discord-clone/web -- security-headers.test.ts security-dashboard.test.ts csp-telemetry-postgres.test.ts`
- `npm run build --workspace @discord-clone/web`
