# T03 Invite Feedback

작성일: 2026-05-13

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| T03 backend worker | Frontend changes appeared during backend status review. | Confirmed they were expected parallel frontend worker changes and integrated both scopes. |
| Backend review | Role grants bypassed assignment authorization and invite accept could consume usage before downstream grant validation. | Added channel/role validation, role grant ceiling checks, and pre-consumption role grant validation with HTTP regression tests. |
| Backend review | Deleted/expired invite deletion used preview and was not idempotent. | Added metadata lookup that does not require invite usability and switched delete authorization to that path. |
| Frontend review | Invite modal lacked dialog semantics/focusability. | Added `role="dialog"`, `aria-modal="true"`, `tabindex="-1"`, and mounted focus. |
| Frontend review | Role grant chips used non-wrapping column grid. | Switched role grants to wrapping flex layout. |

## Known Non-Blocking Risks

- Invite state is in-memory.
- Frontend invite modal is static seed state.
- Audit log is not implemented.
- Delivery/deep-link invite UX is not implemented.
- Background re-review agents were requested after remediation, but no final response was available before commit.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
