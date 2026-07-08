---
id: agent-rules-index
description: Project-local agent rule index.
alwaysApply: true
---
# Agent Rules

Project-local rules live in `.agents/rules/*.md`.

Before work, load rule files whose frontmatter matches the task:

- `alwaysApply: true` means read every session.
- `globs` matches repository paths touched by the task.
- `triggers` matches task intent words.
- `appliesTo` names broad areas such as `backend`, `frontend`, `qa`, `git`, or `docs`.

Keep `AGENTS.md` as the bootstrap and put detailed operating rules here.
