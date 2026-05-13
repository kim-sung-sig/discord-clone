# T02C Guild Authorization Boundary Feedback

작성일: 2026-05-13

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| T02-B backend review | Role/permission mutation endpoints lacked authorization. | Added bearer resolver and owner/manage permission gates. |
| T02-C RED | Signup fixtures used invalid hyphenated usernames. | Converted fixture usernames to underscore format. |
| T02-C RED | Missing membership setup endpoint blocked delegated permission tests. | Added owner-gated member add endpoint. |
| Backend review | `MANAGE_ROLES` delegate could escalate to `ADMINISTRATOR`. | Added permission ceiling and escalation regression tests. |
| Spec review | Delegated overwrite and invalid/missing token cases lacked direct coverage. | Added invalid bearer, missing mutation token, and delegated overwrite assertions. |
| Backend review | Controller method body validation ran before auth. | Reordered controller methods to authenticate before body validation where possible. |
| Spec review | Malformed JSON could return 400 before auth because body binding ran first. | Added guild mutation authentication interceptor. |
| Spec review | Member add endpoint was not listed in design matrix. | Added endpoint rule to T02-C design document. |

## Known Non-Blocking Risks

- Persistence remains in-memory.
- `401`/`403` use Spring default error format.
- Full Spring Security filter chain is deferred; scoped MVC interceptor covers guild mutation authentication first.
- Role hierarchy checks are deferred.
- Frontend is not API-connected.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
