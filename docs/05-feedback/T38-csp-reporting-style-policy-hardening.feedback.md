# T38 CSP Reporting & Style Policy Hardening Feedback

작성일: 2026-05-17  
PDCA Phase: Act  
Slice: T38 CSP Reporting & Style Policy Hardening

## Decisions

- Keep enforce CSP compatible with Nuxt runtime styles for now.
- Use report-only CSP as the safe path to test `style-src 'self'` before enforcement.
- Normalize and redact CSP reports before logging, never storing raw payloads.
- Return `204` for accepted and ignored reports so browsers do not receive reflected content.

## Findings Resolved

| Finding | Resolution |
| --- | --- |
| CSP reporting endpoints were missing. | Added enforce and report-only Nuxt server routes. |
| Report payloads could contain sensitive URLs or script samples. | Added origin/literal normalization and script-sample omission. |
| T26 script nonce policy needed regression coverage. | Added tests asserting nonce-backed script policy and no script `unsafe-inline`. |
| Style hardening needed a low-risk rollout path. | Added strict report-only style policy while keeping enforce style compatibility. |

## Improvement Task Candidates

| Candidate | Priority | Reason |
| --- | --- | --- |
| T51 durable CSP telemetry store | P1 | Current reports are process-log telemetry; persistence enables trend analysis and alerting. |
| T52 style nonce/hash enforcement removal pass | P1 | Report-only data should drive eventual removal of enforce `style-src 'unsafe-inline'`. |
| T53 CSP report rate limiter | P1 | Size/content limits exist, but distributed rate limiting would reduce report endpoint abuse risk. |
| T54 browser security dashboard | P2 | Operators need a UI only after durable telemetry exists. |

## Verification

- `npm test -- --run tests/components/security-headers.test.ts`: PASS
- `npm test -- --run`: PASS
- `npm run build`: PASS
- `npm run e2e`: PASS on fresh port 3109
