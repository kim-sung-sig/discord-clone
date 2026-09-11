---
slug: T104-trusted-proxy-subject-normalization
ticket: T104
phase: analysis
hub: "[[T104-trusted-proxy-subject-normalization]]"
---
> 🧭 [[T104-trusted-proxy-subject-normalization]] · PDCA: [[T104-trusted-proxy-subject-normalization.plan]] → [[T104-trusted-proxy-subject-normalization.design]] → **analysis** → [[T104-trusted-proxy-subject-normalization.report]] → [[T104-trusted-proxy-subject-normalization.feedback]]

# T104 Trusted Proxy Subject Normalization Analysis

## Implementation Notes

- Added `csp-rate-limit-subject.ts`.
- Added tests before implementation for untrusted spoofing, trusted proxy extraction, IPv4-mapped addresses, and env parsing.
- Removed duplicated route-local subject extraction from both CSP report routes.
- Added `.env.example` documentation for trusted proxy configuration.

## Security Review

- Forwarded headers are spoofable and are now ignored by default.
- A deployment must opt in to trusted proxy forwarding with explicit proxy IPs or CIDRs.
- Only normalized IP values are returned; invalid forwarded header values are skipped.
- This change protects both in-memory and Redis-backed CSP report limiters because both receive the same subject string.

## Remaining Gaps

- IPv6 CIDR matching is not implemented; exact IPv6 proxy addresses are supported.
- There is no runtime diagnostic endpoint showing whether a request used direct or trusted-proxy subject derivation.
