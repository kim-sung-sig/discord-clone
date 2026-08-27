# Java Principal Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Java/Spring 백엔드 변경을 8개 지표, 100점, P0/P1/P2, 조건부 인프라 규칙팩으로 평가하는 프로젝트 전용 Codex 스킬을 만든다.

**Architecture:** `.codex/skills/java-principal-review/SKILL.md` 하나에 활성화 조건, 사전 점검, 점수 계산, 인프라 규칙팩, 결과 템플릿을 둔다. 기존 `discord-backend-quality-gate`는 대체하지 않고 관련 저장소 검토의 기본 컨텍스트로 참조한다.

**Tech Stack:** Codex project-local skill Markdown, Java 21/Spring backend conventions.

---

### Task 1: Create the review skill

**Files:**
- Create: `.codex/skills/java-principal-review/SKILL.md`
- Reference: `.codex/skills/discord-backend-quality-gate/SKILL.md`
- Reference: `docs/superpowers/specs/2026-07-14-java-principal-review-design.md`

- [ ] **Step 1: Create YAML frontmatter with the agreed activation contract**

```yaml
---
name: java-principal-review
description: |
  Review Java and Spring backend code, designs, and refactors with eight scored Principal Backend Engineer criteria.
  Triggers: /java-principal-review, Java 코드 검토, Spring 코드 리뷰, 설계 검토, 리팩터링 검토, 운영 관점 검토
argument-hint: "/java-principal-review [file|directory|diff|plan]"
user-invocable: true
allowed-tools:
  - Read
  - Grep
  - Bash
---
```

- [ ] **Step 2: Add the review workflow and score calculation**

Include the seven scored categories (15/15/15/15/15/15/10), the `충족/부분 충족/미충족/검증 불가/해당 없음` calculation rule, all five rejection gates, and the `승인/조건부 승인/반려` thresholds.

- [ ] **Step 3: Add the required review criteria and scope-sensitive rules**

Include concrete checklists for performance, clean code, model-based development, operations, security, plan-to-implementation alignment, and improvements. Require risk-based operational validation and same-module regression evidence. Activate JPA/Hibernate, Kafka, Redis, and distributed-system rules only when their usage is present.

- [ ] **Step 4: Add the exact result template**

Require all eight report sections, P0/P1/P2 only for security, clean code, performance, model-based development, improvements, and operations, plus evidence, unverified items, and residual risks. Keep file reports out of v1.

- [ ] **Step 5: Inspect the completed skill against the approved specification**

Run:

```bash
rg -n "^name: java-principal-review|성능|클린코드|모델 기반|운영|보안|계획|개선|P0|Kafka|Redis|분산" .codex/skills/java-principal-review/SKILL.md
```

Expected: all eight categories, P0/P1/P2, and four infrastructure rule packs are present.

- [ ] **Step 6: Check Markdown and whitespace integrity**

Run:

```bash
git diff --check -- .codex/skills/java-principal-review/SKILL.md
```

Expected: no output and exit code 0.

- [ ] **Step 7: Commit the skill and its design artifacts only after user review**

```bash
git add .codex/skills/java-principal-review/SKILL.md docs/superpowers/specs/2026-07-14-java-principal-review-design.md docs/superpowers/plans/2026-07-14-java-principal-review-implementation-plan.md
git commit -m "feat: add principal Java review skill"
```

Expected: the commit contains only the listed skill design artifacts; unrelated working-tree changes remain unstaged.

## Self-review

- Spec coverage: Task 1 implements the activation, independent eight-category workflow, 100-point scoring, rejection gates, P0/P1/P2 rules, risk-based operations checks, four conditional infrastructure packs, and chat-only result output.
- Placeholder scan: no TBD/TODO or unspecified implementation steps.
- Consistency: the skill name, command, score weights, and file paths match the approved design.
