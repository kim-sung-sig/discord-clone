# T163 Remove Legacy Frontend SBOM Fallback Utility Feedback

Created: 2026-05-22
PDCA Phase: Act

## Feedback Items

| Source | Finding | Decision |
| --- | --- | --- |
| T162 follow-up | The security gate no longer invokes `qa/security-frontend-sbom.mjs`, but the legacy utility remained available. | Remove the utility and make the security contract reject reintroduction. |
| T190 policy boundary | Full local security gate execution is blocked by tenant policy for external dependency metadata transmission. | Verify with contract and policy-only gate; do not claim full vulnerability runtime coverage. |

## PDCA Act Decision

Proceed with local cleanup commit after contract and policy-only verification pass.
