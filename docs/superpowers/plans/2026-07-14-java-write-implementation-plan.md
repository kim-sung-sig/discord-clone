# Java Write Implementation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Java/Spring 쓰기 흐름의 Orchestrator·Activity·Aggregate Root 품질 계약을 안내하는 프로젝트 스킬과 두 Hookify 경고 규칙을 만든다.

**Architecture:** `.codex/skills/java-write-implementation/SKILL.md`에 선택 가능한 쓰기 구조와 필수 책임 경계를 둔다. `.codex/hookify.*.local.md` 두 파일은 Java 백엔드 편집과 작업 종료에서 스킬 실행을 경고한다.

**Tech Stack:** Codex project-local skill Markdown, Hookify local rules, Java 21/Spring backend conventions.

---

### Task 1: Create the write implementation skill

**Files:**
- Create: `.codex/skills/java-write-implementation/SKILL.md`
- Reference: `docs/superpowers/specs/2026-07-14-java-write-implementation-design.md`
- Reference: `backend/modules/message/src/main/java/com/example/discord/message/DefaultPublishMessageUseCase.java`

- [ ] **Step 1: Add frontmatter and activation contract**

```yaml
---
name: java-write-implementation
description: |
  Guide Java/Spring write-flow implementation with an Orchestrator, single-Aggregate-Root Activities, and model-based validation.
  Triggers: /java-write-implementation, Java 구현, 쓰기 UseCase 구현, Command 구현, Orchestrator 구현, Activity 추가
argument-hint: "/java-write-implementation [target or write-flow description]"
user-invocable: true
allowed-tools:
  - Read
  - Grep
  - Bash
---
```

- [ ] **Step 2: Add the implementation contract**

Write the Request/Resolver/Orchestrator flow, optional Dispatcher and ModelPipeline, Activity single Aggregate Root ownership, model-based Validator/Factory/Policy/Activity responsibilities, optional Command objects, transaction ownership, event placement, and three-parameter default.

- [ ] **Step 3: Add the mandatory implementation-check output**

Require target, write flow, Aggregate Root owning Activity, selected optional components and reasons, transaction owner and boundary, parameter exceptions, structural compliance, tests, and residual risks.

- [ ] **Step 4: Verify the skill contract is present**

Run: `rg -n "Orchestrator|Activity|Aggregate Root|Validator|Policy|Factory|ModelPipeline|3개|트랜잭션" .codex/skills/java-write-implementation/SKILL.md`

Expected: every required responsibility and parameter rule appears.

### Task 2: Create Hookify warnings

**Files:**
- Create: `.codex/hookify.warn-java-write-contract.local.md`
- Create: `.codex/hookify.require-java-write-quality-before-stop.local.md`

- [ ] **Step 1: Create the Java edit warning**

Create a `file` event with `action: warn` and a file-path condition matching Java backend source files. Its message tells the agent to apply the implementation contract only when the edit is a write flow.

- [ ] **Step 2: Create the stop warning**

Create a `stop` event with `action: warn` and `pattern: .*`. Its message tells the agent to run `/java-write-implementation` before completing Java write changes and `/java-principal-review` for final-product review.

- [ ] **Step 3: Verify syntax and paths**

Run: `rg -n "^name:|^enabled: true|^event:|^action:|java-write-implementation|java-principal-review" .codex/hookify.warn-java-write-contract.local.md .codex/hookify.require-java-write-quality-before-stop.local.md`

Expected: both rules are enabled, use `warn`, point to the intended events, and have no whitespace errors.

## Self-review

- Spec coverage: Task 1 covers all write implementation responsibilities, optional selections, parameter discipline, output, and scope. Task 2 covers the Java edit and stop warnings.
- Placeholder scan: no TBD/TODO or unspecified files.
- Consistency: the skill name and Hook references are `java-write-implementation`; final review remains `java-principal-review`.
