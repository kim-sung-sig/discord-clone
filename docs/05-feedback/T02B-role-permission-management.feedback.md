# T02B Role/Permission Management Feedback

작성일: 2026-05-13

## Feedback Log

| Source | Feedback | Action |
| --- | --- | --- |
| Backend review | Role/permission mutation endpoints lack authorization. | Deferred as explicit T02-C security scope because controllers do not yet carry authenticated guild requester context. |
| Backend review | JSON `null` request bodies can return default empty 400 responses. | Added request guard plus `HttpMessageNotReadableException` JSON error handler. |
| Backend review | Overwrite null `allow`/`deny` payloads were not tested. | Added MockMvc regression test for null overwrite permission list. |
| Backend re-review | `deny: null` was handled but not directly tested. | Added direct `deny: null` MockMvc assertion. |
| Frontend review | Role panel fixed overlay overlaps shell content. | Moved panel into workspace grid and added workspace containment tests. |

## Known Non-Blocking Risks

- Authorization for mutation APIs is not implemented in T02-B.
- Persistence is still in-memory.
- Frontend is static seed state, not backend-connected.
- Role hierarchy and position constraints are not implemented.
- Toolchain warnings remain non-blocking: Gradle 9 deprecation warning, Nuxt sourcemap warning, Vue package exports deprecation warning.
