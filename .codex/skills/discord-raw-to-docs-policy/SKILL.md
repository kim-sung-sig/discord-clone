---
name: discord-raw-to-docs-policy
description: >
  Use in this discord repository, and as a reusable base for other LLM Wiki projects, when turning raw sources such as code observations, surveys, logs, agent notes, requirements, or source excerpts into durable wiki documentation under Karpathy-style LLM Wiki rules: immutable raw sources, separate synthesis docs, cross-links, index/log updates, explicit contradictions, and no raw dumps.
---
# Discord Raw To Docs Policy

## Core Rule

Follow Karpathy-style LLM Wiki rules for every raw-to-docs promotion: raw sources remain separate, synthesis pages are generated and cross-linked, durable facts are indexed and logged, and contradictions stay explicit.

This skill is project-local for `C:\git\discord`, but its policy is a base pattern other LLM Wiki projects can reuse.

## Source Layers

- Raw: repository observations, survey notes, logs, source excerpts, agent findings, user requirements, and external source notes.
- Synthesis docs: compact wiki pages that connect raw facts into domain language, structure, decisions, risks, and workflows.
- Index/log: navigation and audit trail for future agents.

## Required Promotion Contract

Before raw information becomes documentation, record:

```markdown
## Raw Source
- Source path or URL:
- Observation date:
- Scope:
- Owner or origin:

## Extraction
- Durable facts:
- Domain terms:
- Relationships:
- Decisions or invariants:
- Unverified claims:

## Synthesis Target
- Wiki page:
- Cross-links to add:
- Index update required: yes/no
- Log entry required: yes/no

## Contradictions And Staleness
- Conflicting source:
- Current source of truth:
- Follow-up:
```

## Workflow

1. Keep raw sources immutable or append-only. Do not rewrite raw observations to make them match the synthesis.
2. Extract only durable facts that future agents need before development, review, or operation.
3. Write or update synthesis docs under `wiki\*.md`; use `raw\surveys\*.md` or `raw\sources\*.md` only for broad discovery or source notes.
4. Add cross-links so future agents can navigate from concept to implementation area, risk, or workflow.
5. Update `index.md` when a new durable wiki page is added.
6. Append `log.md` for every durable wiki promotion or correction.
7. Mark contradictions, stale claims, and unverified assumptions instead of hiding them.
8. Hand implementation-facing synthesis to `discord-llm-wiki-synthesis`, `discord-implementation-blueprint`, or `discord-wiki-change-impact` as appropriate.

## Development Workflow Hook

Use this policy before development when the task depends on raw observations rather than an existing synthesis page:

```text
discord-wiki-before-development
-> discord-raw-to-docs-policy
-> discord-llm-wiki-synthesis when graph context is needed
-> discord-implementation-blueprint when code changes are needed
-> implementation and verification
-> discord-wiki-publish-accepted-knowledge after review and verification accept the work
```

Use this policy for natural-language changes when changed wording must become durable project knowledge:

```text
discord-wiki-change-impact
-> discord-raw-to-docs-policy
-> discord-llm-wiki-synthesis when graph context is missing
-> discord-implementation-blueprint when implementation is required
```

## Do Not

- Do not paste large source files, logs, or transcripts into wiki synthesis pages.
- Do not treat raw notes as durable knowledge until they are extracted, linked, and logged.
- Do not overwrite raw evidence to resolve contradictions.
- Do not publish scratch blueprints or temporary reasoning as durable docs.
- Do not skip `index.md` and `log.md` when adding durable wiki pages.
