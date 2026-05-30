# Project Agent Personas

This directory defines project-local subagent personas for the Discord clone.
Use these personas when dispatching up to 10 subagents in one session.

## Roles

- `EXPLORER`: read-only execution, codebase search, browser/QA evidence gathering.
- `DEVELOPER`: implementation with explicit file ownership and test-first workflow.
- `QA-REVIEWER`: spec compliance, code quality, security, UX, and verification review.

## Dispatch Rules

- Follow `docs/03-tasking/subagent-role-packets.md` for packet shape and review gates.
- Keep each task bounded and self-contained.
- Give each `DEVELOPER` explicit allowed write paths and a disjoint write set.
- Tell every editing agent that other agents may be editing nearby files and they must not revert unrelated changes.
- For behavior changes, follow TDD: write the failing test, verify it fails for the expected reason, then implement the minimum passing code.
- After each implementation task, send a diff-only packet to `QA-REVIEWER` in two passes: spec compliance first, then code quality.
- Use multiple `EXPLORER` agents in parallel for independent questions.
- Missing RED/GREEN evidence blocks completion for behavior changes.
- P0/P1 findings block commit until fixed and re-reviewed.
- Shared files require serialized editing, especially `apps/web/stores/shell.ts`, OpenAPI generated files, migrations, auth/permission paths, and cross-layer API changes.
- Do not poll indefinitely for approval. If approval is required, stop and wait for the user to resume the thread.

## Product UI Requirements

Future user-facing screens should be designed so that labels, theme tokens, and configurable screen content can be driven by structured JSON.
The frontend should support locale switching and theme selection without hard-coding copy or colors into workflow logic.
