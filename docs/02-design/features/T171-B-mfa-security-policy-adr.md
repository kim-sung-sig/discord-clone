# T171-B MFA Security Policy ADR

Date: 2026-05-31
Status: Accepted - implemented
Decision scope: MFA requirement for security dashboard reads, operator token lifecycle actions, and future high-risk admin/security operations

## Context

T170 added operator token audit review for the guarded `/security` dashboard. The dashboard now has multiple access paths:

- backend principal verification through `NUXT_SECURITY_DASHBOARD_AUTH_CHECK_URL`
- signed JWT verification through `NUXT_SECURITY_DASHBOARD_JWT_SECRET`
- short-lived server-issued operator tokens
- local development open mode when a configured guard is not required

Operator token bootstrap still uses the configured `NUXT_SECURITY_DASHBOARD_TOKEN` as a break-glass exchange secret. The exchanged dashboard token is short-lived and auditable, and T171-B now requires fresh MFA evidence for high-risk operator token lifecycle actions.

## Decision

Adopt step-up MFA for high-risk security operations first.

1. Require MFA evidence for operator token bootstrap/exchange, operator token revocation, and future privileged security actions.
2. Do not require MFA for every read-only `/security` dashboard view in the first phase, as long as existing backend/JWT/operator-token guard requirements remain enforced.
3. Prefer MFA evidence from the backend identity provider or external IdP, surfaced as a trusted principal claim such as `mfaVerified`, `amr`, `acr`, or `authTime`.
4. Treat local TOTP/WebAuthn enrollment as a later backend-auth feature, not a Nuxt-only security boundary.
5. Keep local development and tests able to simulate MFA through explicit non-production fixtures; production must not accept a static MFA bypass.
6. Require audit events for MFA-gated operator actions without logging raw tokens, raw recovery codes, or MFA secrets.

## Alternatives Considered

### A. Step-Up MFA For High-Risk Actions

This is the proposed option. It protects the highest-impact actions while minimizing operator friction for read-only incident review.

Tradeoff: the first implementation must classify routes by risk and carry MFA evidence through the existing access guard.

### B. MFA For The Entire `/security` Dashboard

This maximizes protection around security telemetry and audit views.

Tradeoff: every incident review requires MFA, which can slow response and complicate break-glass access.

### C. Defer MFA

This keeps the current short-lived operator token model and avoids near-term auth complexity.

Tradeoff: a compromised admin session or bootstrap secret can still reach high-risk operations without a second factor.

## Consequences

Implemented consequences:

- The security dashboard access model gains a second authorization dimension: base authorization plus MFA freshness for high-risk actions.
- Operator token exchange/revoke routes require explicit MFA contracts.
- Existing dashboard read tests remain valid unless a route is reclassified as high-risk.
- Future global-role, security-action, and break-glass flows should reuse the same MFA evidence contract.

Rejected alternative:

- The project should document why short-lived operator tokens and audit logging are sufficient without MFA.
- Future security dashboard work should keep high-risk actions small, auditable, and token-safe.

## Approval Checklist

- [x] MFA is required for high-risk operator/security actions.
- [x] Read-only `/security` views may remain accessible with existing dashboard guards in phase 1.
- [x] MFA evidence must come from backend auth or an external IdP, not frontend-only state.
- [x] Production has no static MFA bypass.
- [x] Recovery/break-glass paths are audited and do not expose raw secrets.

## Implementation Notes

T171-B was implemented with TDD after user approval:

1. Added failing tests showing operator token exchange/revoke reject principals without MFA evidence.
2. Extended the security dashboard access principal model with MFA evidence and freshness metadata.
3. Added operator-token lifecycle route policy enforcement for fresh MFA evidence.
4. Added UI copy for step-up required states without storing MFA secrets or recovery codes in the browser.
5. Added environment controls for MFA max age and explicit non-production test fixture behavior.

## Related Artifacts

- `docs/01-plan/features/T171-security-architecture-decision-prep.plan.md`
- `docs/04-report/T171-B-mfa-decision-review.report.html`
- `docs/04-report/T170-security-dashboard-operator-token-audit-review.report.md`
- `docs/02-design/features/T105-admin-rbac-security-dashboard.design.md`
- `docs/02-design/features/T129-ephemeral-operator-token-flow.design.md`
- `docs/02-design/features/T171-A-trusted-proxy-policy-adr.md`
