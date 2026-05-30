# Project Agent Rules

## Obsidian Wiki Hooks

Use the external Obsidian wiki as a selective context layer, not as a full-context dump.

These hooks are project-local. Do not install or maintain them as global Codex skills.
The project-local skill documents live under:

- `.codex\skills\discord-wiki-before-development\SKILL.md`
- `.codex\skills\discord-wiki-after-development\SKILL.md`

Current wiki root:

`C:\tmp\ObsidianVaults\discord-llm-wiki`

If the user provides a different available Vault path later, use the same file layout there.

### Before Development

Before editing code, identify the task domain and read only the minimum wiki pages needed.

- Always start with `index.md`.
- Read `wiki\Agent Development Guide.md` only when delegating, planning, or choosing verification scope.
- Backend work: read `wiki\Backend Architecture.md` and `backend\AGENTS.md`.
- Frontend, desktop, or shared TypeScript work: read `wiki\Frontend Client Architecture.md`.
- QA, CI, infra, migration, OpenAPI, or runtime work: read `wiki\QA Infra Operations.md`.
- Roadmap, task selection, or residual-risk work: read `wiki\Current Roadmap And Risks.md`.
- Read `raw\surveys\*.md` only when the relevant `wiki\*.md` page is insufficient.

Do not load the entire Vault by default. In the work summary, mention which wiki pages were used.

### After Development

After implementation and verification, update the wiki only for durable knowledge:

- architecture or boundary changes
- new or changed verification commands
- new risk, caveat, or recurring pitfall
- changed roadmap/task status
- new agent operating rule

Update the relevant `wiki\*.md` page, append `log.md`, and update `index.md` only if a new page was added.
Do not paste large source files into the wiki. Link exact repository paths and summarize.

If the external Vault cannot be written, report the intended wiki update explicitly instead of silently skipping it.
