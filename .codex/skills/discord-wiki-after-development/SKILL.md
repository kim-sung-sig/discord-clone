---
name: discord-wiki-after-development
description: Use when finishing implementation, bugfix, refactor, QA change, CI change, documentation-affecting change, or verified development work in this discord repository
---
# Discord Wiki After Development

## Core Rule

After verified development, update the project Obsidian wiki only when the work creates durable knowledge future agents should reuse.

Wiki root:

`C:\tmp\ObsidianVaults\discord-llm-wiki`

## Access Fallback

The Vault is outside the repository root. If a repository-scoped file tool refuses a Vault path as outside the project, use a shell read/write command for only the specific wiki page required by this workflow. Prefer Windows PowerShell with `-LiteralPath` because wiki filenames contain spaces. For reads:

```powershell
C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -Command "Get-Content -Raw -LiteralPath 'C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\QA Infra Operations.md'"
```

Keep updates selective: edit only the relevant `wiki\*.md`, append `log.md`, and update `index.md` only when adding a new page.

## Update When

- Architecture, module boundary, or platform boundary changed.
- Verification command, QA gate, CI behavior, or local runtime setup changed.
- New operational caveat, security rule, recurring pitfall, or residual risk was found.
- Roadmap/task status changed.
- Agent workflow rule changed.
- The wiki is stale because code or docs now contradict it.

## Do Not Update For

- Trivial typo or formatting-only changes.
- Purely local refactors that do not alter future agent decisions.
- Temporary debugging notes.
- Raw logs, dumps, or full source copies.

## Required Flow

1. Update the relevant `wiki\*.md` page.
2. Append `log.md` with date, action, and changed pages.
3. Update `index.md` only if a new page was added.
4. Add a raw survey under `raw\surveys\` only for broad discovery or explicit investigation output.
5. If the Vault cannot be written, report the intended wiki update in the final answer.
6. Mention `Wiki updated: ...` or `Wiki skipped: ...` in the final answer.

## Staleness Rule

Repository code and committed docs win over the wiki. If a contradiction is found, update the wiki and record the correction in `log.md`.
