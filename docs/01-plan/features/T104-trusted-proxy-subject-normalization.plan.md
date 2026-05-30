# T104 Trusted Proxy Subject Normalization Plan

## Objective

Prevent CSP report rate-limit subject spoofing by trusting forwarded IP headers only when the direct peer is an explicitly configured trusted proxy.

## Current State

- CSP report routes derive the limiter subject from `x-forwarded-for` first.
- Browser clients can spoof `x-forwarded-for` unless a trusted proxy boundary is enforced.
- Redis and in-memory limiters already accept a normalized subject string.

## Scope

1. Add a shared CSP rate-limit subject normalization utility.
2. Default to the direct peer address when no trusted proxy is configured.
3. Honor `x-forwarded-for` or `x-real-ip` only for trusted direct proxy peers.
4. Support exact IP and IPv4 CIDR trusted proxy rules.
5. Wire both CSP report routes through the shared utility.

## Acceptance Criteria

- Spoofed forwarded headers are ignored from untrusted peers.
- Trusted proxy peers can provide the original client IP.
- IPv4-mapped remote addresses normalize consistently.
- Trusted proxy configuration is documented in `.env.example`.
- Focused tests, related web tests, full web tests, build, and whitespace checks pass.
