# T06 Nuxt Discord Shell Feedback

작성일: 2026-05-14

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| TDD RED | API contract test expected simple path helpers, but implementation initially exposed only nested helpers. | Added flat aliases such as `authLogin`, `guild`, `channelMessages`, `invitePreview`, and `gatewayEvents` while preserving nested helpers. |
| TDD RED | REST client injection used `fetcher`, while implementation accepted only `fetch`. | Client now accepts either `fetch` or `fetcher` and fails fast if neither is provided. |
| TDD RED | Gateway dispatches without `createdAt` were rejected even though shell tests only need sequence/type/payload. | Normalizer now accepts missing `createdAt` and uses a deterministic fallback timestamp. |
| Responsive QA | Mobile breakpoint hid the channel sidebar entirely. | <=720px CSS now keeps channel navigation visible, compact, and scrollable. |

## Known Non-Blocking Risks

- Full Storybook runtime and CI story publishing are deferred; current scope provides story modules plus import/index tests.
- Visual smoke does not yet perform pixel regression comparison.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
