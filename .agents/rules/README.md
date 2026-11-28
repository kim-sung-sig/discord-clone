---
id: agent-rules-index
description: Project-local agent rule index.
alwaysApply: true
---
# Agent Rules

Project-local rules live in `.agents/rules/**/*.md`.

Before work, load rule files whose frontmatter matches the task:

- `alwaysApply: true` means read every session.
- `globs` matches repository paths touched by the task.
- `triggers` matches task intent words.
- `appliesTo` names broad areas such as `backend`, `frontend`, `qa`, `git`, or `docs`.

## 관련 규칙과 검증

- [Java 코딩 규약](backend/java.md)과 [백엔드 엔지니어링 규칙](../../backend/AGENTS.md)을 백엔드 변경에 적용한다.
- [스타일·아키텍처 가이드](../../docs/03-tasking/style-architecture-governance.md)에서 백엔드·프런트엔드 계약 검사 명령을 확인한다.
- [도메인 컨텍스트 맵](../../CONTEXT-MAP.md)에서 모듈이 쓰는 용어와 원본 소유자를 확인한다.

Prefer nested rule paths for domain-specific rules, for example `.agents/rules/backend/logging.md`.
Keep `AGENTS.md` as the bootstrap and put detailed operating rules here.
