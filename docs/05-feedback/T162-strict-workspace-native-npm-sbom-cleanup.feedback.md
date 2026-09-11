---
slug: T162-strict-workspace-native-npm-sbom-cleanup
ticket: T162
phase: feedback
hub: "[[T162-strict-workspace-native-npm-sbom-cleanup]]"
---
> 🧭 [[T162-strict-workspace-native-npm-sbom-cleanup]] · PDCA: [[T162-strict-workspace-native-npm-sbom-cleanup.plan]] → [[T162-strict-workspace-native-npm-sbom-cleanup.design]] → [[T162-strict-workspace-native-npm-sbom-cleanup.analysis]] → [[T162-strict-workspace-native-npm-sbom-cleanup.report]] → **feedback**

# T162 Strict Workspace Native NPM SBOM Cleanup Feedback

Date: 2026-05-20
Slice: T162 Strict Workspace Native NPM SBOM Cleanup

## Improvement Tasks

| Task | Priority | Reason |
| --- | --- | --- |
| T163 Remove Legacy Frontend SBOM Fallback Utility | P3 | The security gate no longer invokes `qa/security-frontend-sbom.mjs`; removing it later would reduce unused security tooling surface. |

## Notes

- T162 closes the main strict npm workspace SBOM gap without changing OSV scan policy.
