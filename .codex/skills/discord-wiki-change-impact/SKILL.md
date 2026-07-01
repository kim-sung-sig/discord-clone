---
name: discord-wiki-change-impact
description: Use in the discord repository when a wiki page, domain-language article, roadmap note, requirement, or user correction changes feature meaning and Codex must trace the natural-language change to affected graph nodes, repository paths, implementation changes, and verification gates. Use discord-raw-to-docs-policy when changed wording must become durable wiki documentation.
---
# Discord Wiki Change Impact

## Core Rule

Treat changed natural language as a potential implementation change request, not as documentation-only text. Trace meaning before touching code.

If the changed wording should become durable project knowledge, pass it through `discord-raw-to-docs-policy` before publishing or using it as documentation authority.

## Required Output

````markdown
## Changed Language
- Source:
- Old meaning:
- New meaning:
- Ambiguity:

## Semantic Delta
- Changed terms:
- Changed relationships:
- Changed invariants:
- Changed tier/layer ownership:
- Changed acceptance criteria:

## Impacted Graph Nodes
- Node:
- Why affected:
- Repository paths:
- Tests/gates:

## Documentation Promotion
- Raw-to-docs required: yes/no
- Synthesis target:
- Index/log update:

## Implementation Decision
- Code change required: yes/no/unclear
- Reason:
- Required blueprint:
- Required verification:
````

## Workflow

1. Identify the exact changed sentence, term, or rule.
2. Compare old and new meaning in project language.
3. If the changed wording is raw input that must become durable docs, use `discord-raw-to-docs-policy`.
4. Map changed meaning to graph nodes from `discord-llm-wiki-synthesis` or create a minimal graph if none exists.
5. Trace graph nodes to repository paths before editing.
6. If the implementation path is non-trivial, hand off to `discord-implementation-blueprint`.
7. If wording is ambiguous, ask for clarification or record the ambiguity before behavior changes.

## Do Not

- Do not edit code directly from a changed phrase.
- Do not publish changed wording as durable docs without raw-to-docs promotion.
- Do not assume a wiki wording change is behavior-changing without tracing affected nodes.
- Do not ignore contradictions between wiki and code; surface them for `discord-wiki-publish-accepted-knowledge` after review and verification accept the correction.
