---
slug: T128-csp-rate-limit-subject-diagnostics
ticket: T128
phase: analysis
hub: "[[T128-csp-rate-limit-subject-diagnostics]]"
---
> 🧭 [[T128-csp-rate-limit-subject-diagnostics]] · PDCA: [[T128-csp-rate-limit-subject-diagnostics.plan]] → [[T128-csp-rate-limit-subject-diagnostics.design]] → **analysis** → [[T128-csp-rate-limit-subject-diagnostics.report]] → [[T128-csp-rate-limit-subject-diagnostics.feedback]]

# T128 CSP Rate-Limit Subject Diagnostics Analysis

Date: 2026-05-20
PDCA Phase: Analysis
Slice: T128 CSP Rate-Limit Subject Diagnostics

## Findings

- T104 already prevents forwarded-header spoofing by trusting forwarded headers only from configured proxy peers.
- Operators still needed visibility into whether the trusted-proxy branch was active at runtime.
- Showing raw IPs would make the security dashboard a new sensitive network telemetry surface.
- A source label, trusted-proxy match flag, rule count, header presence flags, and hash prefix are enough to diagnose most configuration mistakes.

## Risk Review

| Risk | Control |
| --- | --- |
| Raw IP disclosure | Payload and UI expose only source labels, booleans, counts, and hash prefix. |
| Header spoofing confusion | Diagnostics report trusted-proxy match state separately from header presence. |
| Full hash used as durable identifier | UI exposes only the first 12 hex characters. |
| XSS through diagnostic values | Values are enum/boolean/count/hash and rendered with Vue interpolation. |

## Remaining Gaps

- Diagnostics describe the current guarded dashboard request, not every CSP report request.
- Historical subject distribution remains intentionally unavailable until a privacy-reviewed design exists.
