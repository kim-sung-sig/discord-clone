# T39 Backup, Restore & Migration Drill Plan

작성일: 2026-05-17  
PDCA Phase: Plan  
Slice: T39 Backup, Restore & Migration Drill

## Executive Summary

| 관점 | 내용 |
| --- | --- |
| Problem | T16으로 PostgreSQL persistence가 생겼지만, backup/restore와 migration 실패 복구가 실제로 연습되지 않았다. |
| Solution | seed data backup, restore, Flyway migration rehearsal, destructive migration guardrail, restore 후 API smoke를 하나의 QA drill로 만든다. |
| Function UX Effect | 데이터 손상이나 migration 실패가 발생해도 계정/서버/채널/메시지 핵심 흐름을 복구할 수 있다. |
| Core Value | persistence를 "저장된다"에서 "복구 가능하다"로 승격한다. |

## Scope

- Add PostgreSQL backup script for local/CI drill database.
- Add restore script to a clean database/schema.
- Add migration forward validation through Flyway.
- Add destructive migration guardrail checks.
- Run API smoke after restore.
- Record backup artifact retention and deletion policy.
- Document operator recovery procedure and known limitations.

## Out of Scope

- Cloud provider managed backup configuration.
- Point-in-time recovery for production.
- Cross-region disaster recovery.
- Large data volume performance testing.
- Automated production rollback.

## Success Criteria

- A seeded database can be backed up and restored into a clean target.
- Restored database passes auth/guild/channel/message/invite API smoke.
- Flyway migration validation is part of the drill.
- Destructive migration patterns are detected or explicitly reviewed.
- Backup artifacts have retention, location, and cleanup rules.
- T39 analysis/report records the drill command, result, and residual risks.

## Failure Criteria

- Migration failure leaves no documented recovery path.
- Restore requires undocumented manual DB edits.
- API smoke is not run against the restored database.
- Backup artifacts contain secrets or are kept without retention policy.
- Drill only validates empty schema, not seeded application state.
