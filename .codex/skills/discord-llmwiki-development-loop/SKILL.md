---
name: discord-llmwiki-development-loop
description: Use as the primary user-facing development workflow in this discord repository when a task should run through LLM Wiki context intake, optional raw-to-docs promotion, implementation blueprint, implementation, blueprint-vs-implementation review loop, verification, and accepted-knowledge publishing. Use instead of manually invoking individual wiki skills for normal development work.
---
# Discord LLM Wiki Development Loop

## Core Rule

Run development as a blueprint-centered loop. Do not treat the lower-level wiki skills as user-facing steps unless the user explicitly asks for one primitive.

The implementation follows the accepted blueprint. If implementation or review reveals the blueprint is wrong or incomplete, update the blueprint first, then update implementation.

## Loop

```text
1. Context intake
2. Raw-to-docs promotion when raw material is involved
3. Knowledge synthesis when graph context is missing
4. Implementation blueprint
5. Implementation
6. Blueprint-vs-implementation review
7. Fix loop
8. Verification
9. Accepted-knowledge publishing
```

## Required Flow

1. Use `discord-wiki-before-development` to select the minimum wiki context.
2. Use `discord-raw-to-docs-policy` when the task depends on raw observations, surveys, logs, source notes, or new requirements that must become durable docs.
3. Use `discord-llm-wiki-synthesis` when the area lacks connected domain language, structure graph, tier/layer map, or contradiction notes.
4. Use `discord-implementation-blueprint` before behavior changes.
5. Implement from the blueprint.
6. Review implementation against the blueprint:
   - If implementation differs because the blueprint is wrong, update the blueprint first, then update implementation.
   - If implementation differs because the code is wrong, update implementation.
   - If raw knowledge changed, run raw-to-docs and synthesis before changing the blueprint.
7. Run the verification gates named by the blueprint.
8. Use `discord-wiki-publish-accepted-knowledge` only after review and verification accept the blueprint and implementation.

## Review Contract

Every review loop must answer:

```markdown
## Blueprint Alignment
- Matches blueprint: yes/no
- Mismatch:
- Correct owner of fix: blueprint/implementation/raw-docs/unclear

## Required Loop Action
- Update blueprint:
- Update implementation:
- Re-run synthesis:
- Re-run verification:

## Acceptance
- Accepted blueprint:
- Accepted implementation:
- Accepted verification:
- Publishable knowledge:
```

## Do Not

- Do not implement without a blueprint.
- Do not patch code first when review shows the blueprint is stale.
- Do not publish wiki knowledge before review and verification accept it.
- Do not use `discord-wiki-publish-accepted-knowledge` as a standalone cleanup shortcut for unreviewed work.
- Do not load the whole Vault.
