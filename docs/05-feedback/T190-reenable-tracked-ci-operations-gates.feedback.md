# T190 Re-enable Tracked CI Operations Gates Feedback

Created: 2026-05-21
PDCA Phase: Act

## Feedback Items

| Source | Finding | Decision |
| --- | --- | --- |
| CI recovery follow-up | Security/operations workflow gates must not reference untracked local scripts. | Re-enable `qa-security` only with the complete tracked dependency set. |
| User direction | Do not push CI-failing cases. | Commit locally only after local gates pass; defer push until CI risk is addressed. |
| Local security review | Full vulnerability scan sends dependency names and versions to external npm/OSV services. | Stop for explicit approval before running the full gate. |
| Tenant policy enforcement | User approved the full gate on 2026-05-22, but execution was rejected because dependency/SBOM metadata transmission to external services is disallowed. | Do not bypass the policy. Record a temporary waiver for local commit and keep full runtime scan coverage unclaimed. |
| Runtime debug | npm audit may prepend warning/error text before JSON when the endpoint fails. | Parse the JSON object portion and fail with a clear audit endpoint error. |
| Code Quality/Security Agent | CVSS heuristic, severity-insensitive allowlist, artifact path deletion, no fetch/job timeout, and broad permissions weakened the gate. | Use base-score severity mapping, fail closed on unknown OSV severity, include severity in allowlist matching, constrain artifact paths, bound fetch/job time, and scope permissions. |
| QA/Spec Agent | Direct workflow tracked-script guard does not prove indirect security gate dependencies are tracked. | Add tracked dependency assertions to `qa/security-gate.contract.ps1`. |

## PDCA Act Decision

Proceed with local commit under temporary waiver. The full security gate remains unrun locally because tenant policy rejects the external dependency metadata transmission even with user approval.
