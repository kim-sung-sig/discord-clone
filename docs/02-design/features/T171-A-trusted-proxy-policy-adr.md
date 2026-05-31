# T171-A Trusted Proxy Policy ADR

Date: 2026-05-31
Status: Accepted - implemented 2026-05-31
Decision scope: X-Forwarded-For trust boundary, rate-limit subject derivation, security dashboard privacy

## Context

`X-Forwarded-For` is client-controllable unless the application only accepts it from a verified proxy boundary. T104 already hardened Nuxt CSP report rate-limit subject derivation so forwarded headers are ignored by default and only honored when the direct remote address matches `NUXT_CSP_RATE_LIMIT_TRUSTED_PROXY_CIDRS`. T128 added secret-safe dashboard diagnostics so operators can see source labels and trust state without raw IP/header exposure.

T171-A existed because the broader production policy was not fully decided:

- Whether production must define explicit trusted proxy CIDRs.
- Whether non-trusted forwarded headers should always be ignored.
- Whether raw IP addresses or raw forwarded headers may ever appear in operator UI, reports, logs, or examples.
- Whether proxy misconfiguration should block deployment.
- Whether backend API rate limiting should align with the Nuxt CSP explicit allowlist model instead of trusting private/loopback proxy peers implicitly.

The safe default was accepted on 2026-05-31 when the user asked the agent to continue without waiting for a long mobile review.

## Decision

Use an explicit trusted proxy allowlist for production.

1. Production environments must define the proxy peers or proxy CIDRs that are allowed to supply forwarded client identity.
2. Requests whose immediate peer is not in that allowlist must ignore `X-Forwarded-For` and `X-Real-IP`.
3. Security UI, user-facing reports, and docs must not display raw IP addresses, raw forwarded headers, or full subject hashes.
4. Operator diagnostics may expose only safe metadata: source label, configured/matched booleans, rule count, header presence flags, and short hash prefixes when already approved for that specific panel.
5. Production configuration should fail a contract or startup guard when a deployment declares that it is behind a proxy but does not provide an explicit trusted proxy policy.
6. Development and local QA may keep loopback convenience behavior only when it is explicitly scoped away from production.

## Alternatives Considered

### A. Explicit Production Allowlist

This is the proposed option. It gives the clearest audit boundary and matches the existing Nuxt CSP subject-normalization model.

Tradeoff: operations must update configuration when proxy topology changes.

### B. Trust Private Or Loopback Peers Automatically

This is convenient for local and internal deployments, but it can make production trust boundaries too broad. A private address does not prove that the peer is the intended edge proxy.

Tradeoff: lower setup friction, weaker auditability.

### C. Ignore All Forwarded Headers

This is the safest spoofing posture, but it is usually wrong behind load balancers because many clients collapse into one proxy subject.

Tradeoff: strong spoofing resistance, poor production rate-limit grouping behind shared proxy infrastructure.

## Consequences

- Nuxt CSP handling stays aligned with T104/T128.
- Backend API rate-limit handling now uses the same explicit allowlist model through `discord.trusted-proxy.cidrs` / `DISCORD_TRUSTED_PROXY_CIDRS`.
- Future report/dashboard work must keep raw client network data out of UI by default.
- Production startup validation fails when trusted proxy CIDRs are missing or invalid.

## Approval Checklist

- [x] Production trusted proxy CIDR allowlist is required.
- [x] Non-allowlisted `X-Forwarded-For` and `X-Real-IP` are ignored.
- [x] Raw IP/header values remain absent from security UI, reports, docs, and ordinary logs.
- [x] Production proxy configuration errors block deployment or startup.
- [x] Backend API limiter policy is aligned with the accepted trust model.

## Implementation Completed

Implemented with TDD in the backend operational hardening slice:

1. Added failing backend tests for private-peer spoofing, allowlisted proxy extraction, missing production policy, and invalid production rules.
2. Added `TrustedProxyPolicy` with exact IP and IPv4 CIDR matching, valid forwarded IP extraction, and `X-Real-IP` fallback only from trusted peers.
3. Changed `OperationalHardeningFilter` so private, loopback, and unique-local peers are not trusted implicitly.
4. Added production startup validation for `discord.trusted-proxy.cidrs`.
5. Updated local test helpers to use MockMvc `remoteAddr` instead of spoofable forwarded headers for synthetic test clients.

## Related Artifacts

- `docs/01-plan/features/T104-trusted-proxy-subject-normalization.plan.md`
- `docs/02-design/features/T104-trusted-proxy-subject-normalization.design.md`
- `docs/03-analysis/T104-trusted-proxy-subject-normalization.analysis.md`
- `docs/02-design/features/T128-csp-rate-limit-subject-diagnostics.design.md`
- `docs/01-plan/features/T171-security-architecture-decision-prep.plan.md`
- `docs/04-report/T171-A-x-forwarded-for-risk-review.report.html`
