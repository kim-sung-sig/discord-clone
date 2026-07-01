---
name: discord-wiki-before-development
description: Use when starting implementation, bugfix, refactor, code review preparation, QA change, CI change, planning work, or choosing context in this discord repository before broad file reading or editing. Routes to project-local raw-to-docs policy, LLM Wiki synthesis, implementation blueprint, or wiki change-impact skills when needed.
---
# Discord Wiki Before Development

## Core Rule

Use the project Obsidian wiki as a selective context router before development. Keep this skill lightweight: choose the minimum wiki pages and decide whether another project-local wiki skill is needed.

Wiki root:

`C:\tmp\ObsidianVaults\discord-llm-wiki`

## Access Fallback

The Vault is outside the repository root. If a repository-scoped file tool refuses a Vault path as outside the project, read only the required page with a shell read command. Prefer Git Bash for repository work. PowerShell with `-LiteralPath` is allowed for wiki filenames containing spaces:

```powershell
C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe -NoProfile -Command "Get-Content -Raw -LiteralPath 'C:\tmp\ObsidianVaults\discord-llm-wiki\index.md'"
```

## Required Flow

1. Read only `C:\tmp\ObsidianVaults\discord-llm-wiki\index.md` first.
2. Classify the task domain and likely write scope.
3. Read only the matching synthesis page:
   - Backend: `wiki\Backend Architecture.md` and `C:\git\discord\backend\AGENTS.md`
   - Frontend, desktop, shared TypeScript: `wiki\Frontend Client Architecture.md`
   - QA, CI, infra, migration, OpenAPI, runtime: `wiki\QA Infra Operations.md`
   - Roadmap, task choice, residual risks: `wiki\Current Roadmap And Risks.md`
   - Delegation, planning, verification scope, agent workflow: `wiki\Agent Development Guide.md`
4. Read `raw\surveys\*.md` or `raw\sources\*.md` only if the relevant `wiki\*.md` page is insufficient.
5. Decide whether to route to another project-local skill:
   - Use `discord-raw-to-docs-policy` when raw observations, surveys, logs, source notes, or user requirements must become durable wiki docs.
   - Use `discord-llm-wiki-synthesis` when the area lacks connected domain language, structure graph, or tier/layer map.
   - Use `discord-implementation-blueprint` when code will be implemented from a detailed natural-language design.
   - Use `discord-wiki-change-impact` when a wiki page, requirement, roadmap note, or user correction changes feature meaning.
6. Mention `Wiki used: ...` in the work summary or final answer.

## Do Not

- Do not recursively read the Vault.
- Do not read raw surveys or raw sources before synthesis pages.
- Do not promote raw notes into docs inside this router skill; hand that off to `discord-raw-to-docs-policy`.
- Do not create the implementation graph inside this router skill; hand that off to `discord-llm-wiki-synthesis`.
- Do not write implementation design inside this router skill; hand that off to `discord-implementation-blueprint`.
- Do not implement from a changed phrase until `discord-wiki-change-impact` has traced impacted graph nodes, paths, and tests.
- Do not treat the wiki as source of truth when repository code contradicts it.
- Do not start backend work without `backend\AGENTS.md`.
