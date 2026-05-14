# T12 Onboarding/AutoMod/Audit Feedback

작성일: 2026-05-14

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend TDD | AutoMod must block before message persistence. | Added `MessageController` pre-create moderation decision and MockMvc absence-from-list assertion. |
| Backend TDD | Onboarding must not assign arbitrary role IDs. | Domain grants roles only from selected configured answers. |
| Backend worker | Full suite showed one transient storage test failure. | Isolated rerun and full rerun passed; no storage changes were made. |
| Frontend E2E | Moderation controls must avoid SSR click loss. | Added mounted hydration guards to `ModerationPanel` buttons. |

## Known Non-Blocking Risks

- Spam AutoMod is skeleton-only; keyword AutoMod is implemented.
- Audit log persistence and all-write coverage are deferred.
- Moderation panel does not yet manage full policy CRUD beyond deterministic shell actions.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
