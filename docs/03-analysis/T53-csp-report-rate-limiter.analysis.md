# T53 CSP Report Rate Limiter Analysis

Date: 2026-05-18
Slice: T53 CSP Report Rate Limiter

## Analysis

The CSP report endpoints accept browser-generated JSON payloads and intentionally return no content. Before T53, the endpoint had body size and content-type guards, but an attacker could still send many valid small reports and fill process-local telemetry with low-value noise.

The limiter is placed before normalization so rate-limited requests do not spend JSON parsing work and do not create telemetry entries. It is also dependency-injected rather than hard-coded into normalization utilities, which keeps tests deterministic and preserves a future path to a Redis-backed implementation.

## Trade-Offs

- Fixed-window limiting is simple and sufficient for this frontend endpoint slice, but it can allow short boundary bursts.
- In-memory state protects a single Nuxt process only. It does not coordinate multiple frontend instances.
- Returning `204` instead of `429` keeps the endpoint quiet for browsers and avoids exposing policy details, but it means operators need separate telemetry to see limited counts.

## Security Notes

- Subject strings are trimmed and bounded to 128 characters.
- The limiter stores only subjects and counters, not raw report payloads.
- Limited reports are not persisted to CSP telemetry.

## Residual Risk

Distributed abuse protection still needs a shared store or edge/WAF policy. That is registered as a follow-up task because T53 is scoped to application behavior and unit-testable endpoint protection.
