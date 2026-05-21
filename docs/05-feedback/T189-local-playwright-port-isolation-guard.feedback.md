# T189 Local Playwright Port Isolation Guard Feedback

Created: 2026-05-21
PDCA Phase: Act
Slice: T189 Local Playwright Port Isolation Guard

## Feedback Items

| Source | Finding | Decision |
| --- | --- | --- |
| T166/T38 analyses | Local e2e could reuse an unrelated `localhost:3000` app. | Make root e2e self-isolate on a free port. |
| T189 runtime debug | Direct `.cmd` spawn produced `spawn EINVAL` in the local Windows/Node environment. | Use Windows shell command string with explicit quoting. |
| T189 runtime debug | `shell: true` with child-process args produced Node `DEP0190`. | Avoid shell mode for the normal npm-run path. |
| Code Quality Agent | Windows shell command construction created avoidable command-injection risk for passthrough Playwright args. | Spawn `process.execPath` with `npm_execpath` and structured args; contract now forbids `windowsCommand` and `shell: true`. |
| User feedback | Subagent and TDD setup was not visible enough. | Add project-local subagent role packet doc and contract before continuing. |

## PDCA Act Decision

Proceed with T189 completion only after subagent review has no unresolved P0/P1 findings and final diff checks pass.
