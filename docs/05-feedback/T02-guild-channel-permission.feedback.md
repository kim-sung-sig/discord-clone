# T02 Guild/Channel/Permission Feedback

작성일: 2026-05-13

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend review | `@everyone` overwrite precedence must not override stricter member role denies. | Fixed calculator order and added regression test. |
| Backend review | Missing channel `type` should be 400, not server error. | Added controller validation and REST test. |
| Backend review | Architecture guard should catch module-to-boot adapter imports. | Added supplemental source scan test. |
| Frontend review | Channel navigation must be interactive and keyboard accessible. | Converted channel rows to buttons and added focus/disabled styles. |
| Frontend review | Messages must follow selected channel. | Added active-channel message filtering and component assertions. |
| Frontend review | Test selectors should not depend on channel names. | Switched selectors to stable channel ids. |
| QA | Nuxt hydration can race Playwright clicks. | Added mounted hydration guard and e2e wait. |

No unresolved feedback remains for T02-A.

## Known Non-Blocking Risks

- Backend state is in-memory.
- Frontend shell uses static seed state.
- Permission overwrites are role-level only.
- Build tools emit non-blocking deprecation/sourcemap warnings.
