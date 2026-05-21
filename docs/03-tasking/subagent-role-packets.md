# Subagent Role Packets

Created: 2026-05-21
Purpose: Provide a compact project-local role setup for subagent-driven development in the Discord clone repository.

## Required Operating Rule

For non-trivial implementation work, the coordinator must use this sequence:

1. Create or identify the task Plan/Design and allowed write paths.
2. Run TDD locally or assign a Developer Agent a test-first implementation packet.
3. Send a Diff-Only Review Packet to a QA/Spec Agent.
4. Send a separate Diff-Only Review Packet to a Code Quality Agent.
5. Send verification commands and artifact paths to an Observer/Test Agent.
6. Fix P0/P1 findings before commit.
7. Commit only task-owned paths. Push only when local and CI-risk gates allow it.

Do not dispatch agents with the whole chat history. Give each agent only the role packet, task docs, changed paths, targeted diffs, commands, and artifact paths it needs.

## Coordinator

Owns task selection, scope, context packets, review ordering, final integration, commit/push gate, and user checkpoint decisions.

Coordinator must not:

- Reimplement assigned subagent work unless the subagent is blocked and the task is reassigned.
- Ignore P0/P1 review findings.
- Stage unrelated dirty work.
- Push when CI is known or likely to fail.

## Developer Agent

Use for bounded implementation.

Packet:

```text
Role: Developer Agent
Task ID:
Goal:
Required docs:
Allowed write paths:
Read-only context paths:
Forbidden changes:
TDD requirement:
- Add or update a failing contract/test first.
- Report the RED command and failure.
- Implement the minimum change.
- Report the GREEN command and result.
Expected tests:
Return format:
- status: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED
- changed files
- RED evidence
- GREEN evidence
- residual risks
```

## QA/Spec Agent

Use after implementation, before quality review.

Packet:

```text
Role: QA/Spec Agent
Task ID:
Plan/Design paths:
Success criteria:
Changed files:
Diff stat:
Targeted diff:
Tests run:
Known residual risks:
Return format:
- status: APPROVED | CHANGES_REQUESTED
- P0/P1/P2 findings
- missing criteria
- over-scope changes
- required fix or acceptable deferral
```

## Code Quality Agent

Use after QA/Spec approval.

Packet:

```text
Role: Code Quality Agent
Task ID:
Changed files:
Targeted diff:
Relevant architecture rules:
Security constraints:
Tests run:
Return format:
- status: APPROVED | CHANGES_REQUESTED
- P0/P1/P2 findings with file and line
- race/idempotency/security risks
- maintainability risks
- required fix or acceptable deferral
```

## Observer/Test Agent

Use for verification planning, logs, and command evidence. This role does not edit code unless explicitly reassigned.

Packet:

```text
Role: Observer/Test Agent
Task ID:
Verification scope:
Commands to run:
Artifact paths:
Environment assumptions:
Return format:
- status: PASS | FAIL | BLOCKED
- commands run
- key output lines
- artifact paths
- suspected cause for failures
```

## Runtime QA Agent

Use for backend/frontend/e2e runtime gates.

Packet:

```text
Role: Runtime QA Agent
Task ID:
Verification scope:
Commands to run:
Artifact paths:
Environment assumptions:
Expected runtime boundaries:
Return format:
- status: PASS | FAIL | BLOCKED
- commands run
- key output lines
- artifact paths
- cleanup actions performed
- residual runtime risks
```

Preferred commands:

```powershell
npm.cmd test --workspaces
npm.cmd run build --workspace @discord-clone/web
npm.cmd run e2e:web:isolated -- --project=chromium --workers=1 <specs>
powershell -NoProfile -ExecutionPolicy Bypass -File qa/agent-harness.ps1 -Tool <tool-id>
```

## Review Gate

Commit is blocked when:

- QA/Spec Agent returns any P0/P1 finding.
- Code Quality Agent returns any P0/P1 finding.
- Observer/Test Agent reports failed required gates.
- TDD RED/GREEN evidence is missing for behavior changes.
- The staged diff contains unrelated dirty work.

## Current Role Mapping

| Role | Primary use | Typical write access |
| --- | --- | --- |
| Developer Agent | Implement one bounded task | Task-owned files only |
| QA/Spec Agent | Check Plan/Design alignment | Read-only |
| Code Quality Agent | Review architecture/security/maintainability | Read-only |
| Observer/Test Agent | Run or inspect verification logs | Read-only, artifacts only |
| Runtime QA Agent | Runtime backend/frontend/e2e gates | Read-only, artifacts only |
