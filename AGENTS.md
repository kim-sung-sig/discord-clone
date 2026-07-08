# Project Agent Rules

## Project Rule Files

Detailed project-local rules live in `.agents/rules/**/*.md`.

- Read `.agents/rules/README.md` before implementation, review, QA, runtime, documentation, or agent-rule work.
- Load matching rule files by frontmatter: `alwaysApply`, `globs`, `triggers`, and `appliesTo`.
- Prefer nested rule paths such as `.agents/rules/backend/logging.md` for domain-specific rules.
- Keep this `AGENTS.md` as the bootstrap; prefer adding detailed reusable rules under `.agents/rules/`.

## Shell And Command Policy

For this repository, run agent shell commands through Git Bash by default.

- Use `C:\Program Files\Git\bin\bash.exe` as the shell for repository work.
- In tool calls that support an explicit shell, set `shell` to `C:\Program Files\Git\bin\bash.exe`.
- Use Git Bash/POSIX command forms such as `sed`, `rg`, `find`, `./gradlew`, and `/c/...` paths when practical.
- Do not use PowerShell or `pwsh` for ordinary repository reads, edits, Git operations, Gradle runs, or Node/npm commands.
- Use PowerShell or `pwsh` only when a Windows-specific repository QA script requires it, for example an existing `qa/*.ps1` contract, or when Git Bash cannot inspect the Windows-only state needed for the task.

## Gradle Policy

Use the checked-in Gradle wrapper instead of a system Gradle install.

- Prefer `./gradlew` from the repository root.
- For backend-local wrapper work, use `cd backend && ./gradlew ...` when that is the scoped command.
- The preferred sandbox approval prefix for Gradle work is `["./gradlew"]`.
- First verification should be `./gradlew --version`, followed by `./gradlew test` or a focused backend test command that matches the task.
- If Gradle needs to download dependencies and the sandbox blocks network or cache access, rerun the same command with an explicit escalation request and the `["./gradlew"]` prefix rule.

## Git Approval Policy

Do not request or rely on blanket permission for every `git` command. Keep safe reads, normal writes, remote operations, and destructive operations separate.

- Safe read candidates: `git status`, `git diff`, `git log`, `git show`, `git branch`, and `git remote -v`.
- Remote candidates: `git fetch`, `git pull`, and `git push`.
- Working-tree write candidates: `git add` and `git commit`.
- Destructive commands require separate explicit approval every time, including `git reset --hard`, `git clean -fd`, force push, or any command intended to discard user work.

## Environment Verification Order

When changing or validating local agent execution setup, verify in this order:

1. Confirm Git Bash direct execution.
2. Run `./gradlew --version`.
3. Run `./gradlew test` or the focused backend test needed for the task.
4. Run `git status --short`.
5. If needed, verify `git fetch` or `git push` through the explicit approval flow.
6. During subsequent work, check that routine commands are not falling back to PowerShell.

## Obsidian Wiki Hooks

Use the external Obsidian wiki as a selective context layer, not as a full-context dump.

These hooks are project-local. Do not install or maintain them as global Codex skills.
The primary project-local skill documents live under:

- `.codex\skills\discord-llmwiki-development-loop\SKILL.md`
- `.codex\skills\discord-wiki-before-development\SKILL.md`
- `.codex\skills\discord-raw-to-docs-policy\SKILL.md`
- `.codex\skills\discord-llm-wiki-synthesis\SKILL.md`
- `.codex\skills\discord-implementation-blueprint\SKILL.md`
- `.codex\skills\discord-wiki-change-impact\SKILL.md`
- `.codex\skills\discord-wiki-publish-accepted-knowledge\SKILL.md`

Current wiki root:

`C:\tmp\ObsidianVaults\discord-llm-wiki`

If the user provides a different available Vault path later, use the same file layout there.

### Development Loop

For normal implementation, bugfix, refactor, QA, CI, runtime, or documentation-affecting work, use the project-local LLM Wiki development loop as the user-facing workflow:

`discord-llmwiki-development-loop`

This loop wraps selective wiki context, raw-to-docs promotion, synthesis, blueprint writing, implementation, blueprint-vs-implementation review, verification, and accepted-knowledge publishing.

### Before Development Primitive

When the loop or a narrow primitive task needs context selection, identify the task domain and read only the minimum wiki pages needed.

- Always start with `index.md`.
- Read `wiki\Agent Development Guide.md` only when delegating, planning, or choosing verification scope.
- Backend work: read `wiki\Backend Architecture.md` and `backend\AGENTS.md`.
- Frontend, desktop, or shared TypeScript work: read `wiki\Frontend Client Architecture.md`.
- QA, CI, infra, migration, OpenAPI, or runtime work: read `wiki\QA Infra Operations.md`.
- Roadmap, task selection, or residual-risk work: read `wiki\Current Roadmap And Risks.md`.
- Read `raw\surveys\*.md` only when the relevant `wiki\*.md` page is insufficient.

Do not load the entire Vault by default. In the work summary, mention which wiki pages were used.

### Accepted Knowledge Publishing

After the LLM Wiki development loop review and verification accept the work, update the wiki only for durable knowledge:

- architecture or boundary changes
- new or changed verification commands
- new risk, caveat, or recurring pitfall
- changed roadmap/task status
- new agent operating rule

Update the relevant `wiki\*.md` page, append `log.md`, and update `index.md` only if a new page was added.
Do not paste large source files into the wiki. Link exact repository paths and summarize.

If the external Vault cannot be written, report the intended wiki update explicitly instead of silently skipping it.

## Report Document Policy

When creating user-facing reports from now on:

- Write the report body in Korean.
- Prefer an interactive HTML report instead of plain Markdown when the user asks for a report or review document.
- Base report layout on `docs\04-report\templates\korean-interactive-report-template.html`.
- Required report interactions: table of contents, search, and dark mode.
- Keep agent-machine handoff documents as Markdown when they are meant to be read by the next agent.
