---
name: discord-llm-wiki-synthesis
description: Use in the discord repository when scattered code, wiki pages, docs, surveys, or agent notes must be turned into an LLM Wiki knowledge graph with domain language, structure relationships, tier/layer ownership, diagrams, stale facts, and durable synthesis candidates. Use after discord-raw-to-docs-policy when raw sources must first be promoted under Karpathy-style rules.
---
# Discord LLM Wiki Synthesis

## Core Rule

Turn already-selected information into a compact, cross-linked implementation knowledge graph. Keep raw sources separate from synthesis. Repository code and committed docs win over the wiki when they conflict.

Use `discord-raw-to-docs-policy` first when the task starts from raw observations, survey notes, logs, source excerpts, agent findings, or user requirements that must become durable documentation.

## Inputs

- Relevant wiki synthesis page selected by `discord-wiki-before-development`.
- Raw-to-docs promotion contract from `discord-raw-to-docs-policy`, when raw material is involved.
- Relevant repository paths, tests, generated contracts, or raw survey pages.
- User-provided domain wording or requirement notes.

## Required Output

Produce a synthesis note with these sections:

````markdown
## Domain Language
- Term:
- Team meaning:
- Aliases:
- Code identifiers:
- Related wiki links:

## Tier And Layer Map
- UI/client:
- Desktop/native:
- API/controller:
- Application/service:
- Domain:
- Persistence/adapter:
- Infrastructure/runtime:
- QA/contracts:

## Structure Graph
```mermaid
classDiagram
```

## Behavior Flow
```mermaid
flowchart TD
```

## Source Links
- Repository paths:
- Wiki pages:
- Raw surveys:
- Raw sources:

## Staleness And Contradictions
- Claim:
- Source that contradicts it:
- Required follow-up:
````

## Workflow

1. Start from the selected synthesis page. Do not load the whole Vault.
2. If the needed facts are still raw, use `discord-raw-to-docs-policy` before writing synthesis.
3. Collect only the repository paths needed to understand the target capability.
4. Extract terms, aliases, class/component names, service boundaries, endpoints, stores, generated clients, tables, tests, and scripts.
5. Connect nodes by responsibility and data/control flow.
6. Mark contradictions instead of silently resolving them.
7. If the synthesis becomes accepted durable knowledge through the development loop, hand it to `discord-wiki-publish-accepted-knowledge` after review and verification.

## Do Not

- Do not paste large source files into the wiki.
- Do not bypass `discord-raw-to-docs-policy` when raw material is being promoted to docs.
- Do not invent graph nodes not backed by code, docs, raw sources, or user requirements.
- Do not update implementation from synthesis alone; use `discord-implementation-blueprint` or `discord-wiki-change-impact` first.
