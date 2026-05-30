---
name: discord-wiki-before-development
description: Use when starting implementation, bugfix, refactor, code review preparation, QA change, CI change, or planning work in this discord repository before broad file reading or editing
---
# Discord Wiki Before Development

## Core Rule

Use the project Obsidian wiki as a selective context layer before development. Do not load the whole Vault.

Wiki root:

`C:\tmp\ObsidianVaults\discord-llm-wiki`

## Access Fallback

The Vault is outside the repository root. If a repository-scoped file tool refuses a Vault path as outside the project, read the required page with a shell read command instead. Prefer Windows PowerShell with `-LiteralPath` because wiki filenames contain spaces:

```powershell
C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -Command "Get-Content -Raw -LiteralPath 'C:\tmp\ObsidianVaults\discord-llm-wiki\index.md'"
```

For wiki pages:

```powershell
C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -Command "Get-Content -Raw -LiteralPath 'C:\tmp\ObsidianVaults\discord-llm-wiki\wiki\Agent Development Guide.md'"
```

This is an allowed project-local fallback for reading the selective wiki pages listed below. Do not use it to dump the whole Vault.

## Required Flow

1. Read only `C:\tmp\ObsidianVaults\discord-llm-wiki\index.md` first.
2. Classify the task domain.
3. Read only the matching page:
   - Backend: `wiki\Backend Architecture.md` and `C:\git\discord\backend\AGENTS.md`
   - Frontend, desktop, shared TypeScript: `wiki\Frontend Client Architecture.md`
   - QA, CI, infra, migration, OpenAPI, runtime: `wiki\QA Infra Operations.md`
   - Roadmap, task choice, residual risks: `wiki\Current Roadmap And Risks.md`
   - Delegation, planning, verification scope: `wiki\Agent Development Guide.md`
4. Read `raw\surveys\*.md` only if the relevant `wiki\*.md` page is insufficient.
5. Mention `Wiki used: ...` in the work summary or final answer.

## Do Not

- Do not recursively read the Vault.
- Do not read raw surveys before synthesis pages.
- Do not treat the wiki as source of truth when repository code contradicts it.
- Do not start backend work without `backend\AGENTS.md`.
