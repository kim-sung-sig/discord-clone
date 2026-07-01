---
name: discord-wiki-publish-accepted-knowledge
description: Use inside the discord LLM Wiki development loop after blueprint-vs-implementation review and verification have accepted the work, when durable accepted knowledge should be published to the project Obsidian wiki. Do not use for unreviewed or unverified scratch notes.
---
# Discord Wiki Publish Accepted Knowledge

## Core Rule

Publish only accepted knowledge after the development loop has converged. The source of publishable truth is the final accepted blueprint, implementation diff, review decision, and verification evidence.

Repository code and committed docs win over the wiki. If a contradiction is found, publish the correction and record it in `log.md`.

Wiki root:

`C:\tmp\ObsidianVaults\discord-llm-wiki`

## Access Fallback

The Vault is outside the repository root. If a repository-scoped file tool refuses a Vault path as outside the project, use a shell read/write command for only the specific wiki page required by this workflow. Prefer Git Bash for repository work. PowerShell with `-LiteralPath` is allowed for wiki filenames containing spaces.

## Publish Inputs

- Accepted implementation blueprint.
- Implementation diff or changed paths.
- Review result from `discord-llmwiki-development-loop`.
- Verification commands and outcomes.
- Raw-to-docs or synthesis output when durable knowledge changed.

## Publish When

- Architecture, module boundary, or platform boundary changed.
- Verification command, QA gate, CI behavior, or local runtime setup changed.
- New operational caveat, security rule, recurring pitfall, or residual risk was found.
- Roadmap/task status changed.
- Agent workflow rule changed.
- The wiki is stale because code or docs now contradict it.
- Raw-to-docs promotion created durable project knowledge under Karpathy-style LLM Wiki rules.
- Accepted blueprint/review work created durable domain language, layer ownership, structure relationships, verification gates, or recurring risks.

## Required Flow

1. Identify the final accepted knowledge, not intermediate reasoning.
2. Update the relevant `wiki\*.md` page.
3. Append `log.md` with date, action, changed pages, and verification evidence.
4. Update `index.md` only if a new page was added.
5. Add a raw survey under `raw\surveys\` or a source note under `raw\sources\` only for broad discovery or explicit investigation output.
6. For agent workflow changes, update `wiki\Agent Development Guide.md`; add a separate page only when the workflow is too large for the guide.
7. If the Vault cannot be written, report the intended wiki update in the final answer.
8. Mention `Wiki updated: ...` or `Wiki skipped: ...` in the final answer.

## Do Not

- Do not publish scratch reasoning, temporary diagrams, raw dumps, or unverified assumptions.
- Do not publish raw observations that have not passed through `discord-raw-to-docs-policy`.
- Do not publish implementation notes that failed review.
- Do not update `index.md` unless a new durable page was added.
