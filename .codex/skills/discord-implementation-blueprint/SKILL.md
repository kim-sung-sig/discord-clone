---
name: discord-implementation-blueprint
description: Use in the discord repository before implementing a feature, bugfix, refactor, QA change, or natural-language requirement when work should be driven by a detailed implementation article with participating classes/components, relationships, tier/layer responsibilities, invariants, and verification gates. Ensure raw-to-docs promotion is handled first when implementation depends on raw observations or requirements.
---
# Discord Implementation Blueprint

## Core Rule

Before editing behavior, write a concise implementation article that another agent could implement from. The article is the bridge between wiki knowledge and code changes.

If the blueprint depends on raw observations, survey notes, source excerpts, logs, or new user requirements, use `discord-raw-to-docs-policy` before treating those facts as durable project docs.

## Required Inputs

- Wiki pages selected by `discord-wiki-before-development`.
- Raw-to-docs promotion contract from `discord-raw-to-docs-policy`, when raw material is involved.
- Knowledge graph or domain terms from `discord-llm-wiki-synthesis` when available.
- Change-impact analysis from `discord-wiki-change-impact` when the request comes from changed wording.

## Blueprint Shape

````markdown
# <Capability Or Change>

## Approval Gate
- Status: Draft | Questions Open | Approved
- Approver:
- Blocking ambiguity:
- Competing plan branches:

## Goal
- User/business capability:
- Non-goals:

## Domain Language
- Term:
- Meaning in this project:
- Code identifiers:

## Participating Code
- Class/component/store/controller/service/repository:
- Responsibility:
- Depends on:
- Called by:
- Tests:

## Expected Changed Files
- Path:
- Expected change:
- Confidence:

## Tier And Layer Responsibilities
- UI/client:
- Desktop/native:
- API/controller:
- Application/service:
- Domain:
- Persistence/adapter:
- Infrastructure/runtime:
- QA/contracts:

## Structure Diagram
```mermaid
classDiagram
```

## System Flow Diagram
```mermaid
flowchart TD
```

## Behavior Flow
```mermaid
flowchart TD
```

## Invariants And Boundaries
- Auth/security:
- Transactions/idempotency:
- Error handling:
- Generated contracts:
- Platform boundaries:

## Implementation Steps
- Path:
- Change:
- Reason:

## Verification Gates
- Focused tests:
- Contract checks:
- Runtime checks:

## Review Score Preset
- Preset:
- Pass threshold:
- Categories:
````

## Approval Gate

Implementation is blocked until the blueprint is approved.

Stop after the blueprint and ask questions when any of these are true:

- `Approval Gate` status is not `Approved`.
- `Blocking ambiguity` is not empty.
- More than one viable implementation branch exists and no branch is selected.
- The class diagram or system/behavior flow diagram is missing for a multi-class, multi-component, or cross-layer change.
- Expected changed files are missing or too vague to review.
- Verification gates are missing for non-trivial logic.

Use `Status: Questions Open` when user input is required. Do not edit behavior files until the blocking questions are answered and the blueprint is updated to `Status: Approved`.

## Review Score Presets

Every review report must name one preset, score against objective evidence, and include a total.

### Implementation Review

Use for completed feature, bugfix, refactor, QA, CI, runtime, or docs-affecting work.

| Category | Points | Evidence |
| --- | ---: | --- |
| Blueprint alignment | 20 | Implemented paths and behavior match the approved blueprint. |
| Correctness | 20 | Acceptance criteria pass and edge cases are handled. |
| Security/privacy | 15 | Auth, authorization, secrets, logging, data exposure, and trust boundaries are checked when relevant. |
| Tests/verification | 20 | Focused tests, contract checks, build/lint/runtime smoke match the change risk. |
| Maintainability | 15 | Change fits existing boundaries without unneeded abstractions. |
| Operations/docs | 10 | Generated contracts, runbooks, wiki/report updates, and residual risks are current when relevant. |

Pass threshold: 80/100, with no P0/P1 finding and no category below half credit.

### Plan Review

Use before implementation.

| Category | Points | Evidence |
| --- | ---: | --- |
| Scope clarity | 20 | Goal, non-goals, acceptance/failure criteria are testable. |
| Architecture fit | 20 | Tier/layer responsibilities and diagrams match existing system boundaries. |
| File impact | 15 | Expected changed files and ownership are specific enough to review. |
| Ambiguity control | 15 | Open questions, branch choices, and assumptions are explicit. |
| Verification plan | 20 | Cheapest meaningful checks are named before implementation. |
| Risk handling | 10 | Security, data loss, migration, concurrency, and rollout risks are considered when relevant. |

Pass threshold: 85/100. Below threshold means `Approval Gate` stays `Draft` or `Questions Open`.

### Security Review

Use for auth, token, permission, CSP, dependency, upload, webhook, admin, or trust-boundary changes.

| Category | Points | Evidence |
| --- | ---: | --- |
| Threat coverage | 20 | Abuse paths, attacker-controlled inputs, and trust boundaries are enumerated. |
| Access control | 20 | Authn/authz/session semantics are verified. |
| Data exposure | 15 | Secrets, tokens, PII, logs, and response payloads are checked. |
| Regression tests | 20 | Negative tests cover bypass and leakage paths. |
| Operational safety | 15 | Rate limits, audit trails, observability, and failure modes are considered. |
| Dependency/config safety | 10 | Third-party, CSP, env, and generated config changes are reviewed when relevant. |

Pass threshold: 90/100, with no unresolved high-severity finding.

## Workflow

1. Inspect the named repository paths before writing the blueprint.
2. Confirm raw-derived facts passed through `discord-raw-to-docs-policy` if they will drive implementation.
3. Keep the blueprint scoped to the requested capability.
4. Prefer existing module boundaries and local patterns.
5. Use diagrams only when they clarify relationships.
6. Implement only after the blueprint is approved and identifies affected paths and verification gates.

## Do Not

- Do not use raw notes as implementation authority before raw-to-docs promotion.
- Do not use the blueprint to justify broad unrelated refactors.
- Do not skip tests because the blueprint is documentation.
- Do not treat wiki claims as authoritative when code contradicts them.
