# T16 Persistence/PostgreSQL Migration Plan

작성일: 2026-05-14  
PDCA Phase: Plan  
Slice: T16 Persistence/PostgreSQL Migration

## Problem

| 관점 | 내용 |
| --- | --- |
| User Problem | 현재 기능은 런타임 재시작 시 상태가 사라져 실제 서비스처럼 사용할 수 없다. |
| Product Problem | Discord-like 핵심 데이터인 계정, 길드, 채널, 메시지, 초대가 영속화되지 않으면 다음 실연동/운영 단계가 불가능하다. |
| Engineering Problem | in-memory service가 도메인 행위를 검증하는 데는 충분했지만, 트랜잭션, 유니크 제약, cursor pagination, 동시성 보장은 DB adapter가 필요하다. |
| Core Value | PostgreSQL을 source of truth로 올려 T23 실 API UI 연동과 운영/관측/보안 작업의 기반을 만든다. |

## Scope

- PostgreSQL schema migration baseline.
- Repository port/adapters for identity/auth, guild/channel/role, message, invite.
- Testcontainers or local PostgreSQL integration test profile.
- Docker Compose credentials alignment: `dev_user` / `dev_password`.
- `qa/api-smoke.ps1` persistence-backed pass.

## Out of Scope

- Full migration of every skeleton domain in one pass.
- Sharding, partitioning automation, and production backup/restore.
- OpenSearch message indexing.

## Success Criteria

- Signup/login profile survives backend restart.
- Guild/channel/message/invite critical path survives backend restart.
- Message pagination is cursor-based and deterministic from PostgreSQL.
- Duplicate reaction/thread/role constraints are represented at DB level where included.
- Integration tests use PostgreSQL.

## Failure Criteria

- Tests only verify in-memory adapters.
- In-memory state remains active source of truth for critical persisted entities.
- DB schema lacks uniqueness constraints for identifiers that domain assumes unique.
- Runtime API smoke cannot run against the persistence profile.
