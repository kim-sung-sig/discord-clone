# T45 Admin Console & Role Permission UX Feedback

Date: 2026-05-18
Slice: T45 Admin Console & Role Permission UX

## Feedback Items

| Id | Priority | Observation | Proposed Task |
| --- | --- | --- | --- |
| T45-FB-001 | High | Permission mutation is shell-local and not backed by REST/OpenAPI. | T76 Admin permission mutation REST/OpenAPI contract. |
| T45-FB-002 | High | Role hierarchy bypass prevention is not enforced in the web preview. | T77 role hierarchy guard and preview mismatch check. |
| T45-FB-003 | Medium | Channel overwrites are visible but not fully editable. | T78 channel overwrite editor UX. |
| T45-FB-004 | High | Privileged audit feedback is local shell state, not backend audit integration. | T79 privileged action audit API integration. |
| T45-FB-005 | Medium | Role ordering is displayed implicitly by array order, not actively managed. | Add role ordering drag/drop and hierarchy-aware diff preview after API contract exists. |

## Loop Decision

T45 scored 27/30 and passed the threshold. Continue to T46 unless the immediate priority is hardening T45 with backend-backed permission mutation.
