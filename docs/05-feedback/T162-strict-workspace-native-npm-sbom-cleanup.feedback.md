# T162 Strict Workspace Native NPM SBOM Cleanup Feedback

Date: 2026-05-20
Slice: T162 Strict Workspace Native NPM SBOM Cleanup

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T163 Remove Legacy Frontend SBOM Fallback Utility | P3 | The security gate no longer invokes `qa/security-frontend-sbom.mjs`; removing it later would reduce unused security tooling surface. |

## Notes

- T162 closes the main strict npm workspace SBOM gap without changing OSV scan policy.
