# T119 Global Admin Audit Log Plan

Date: 2026-05-19
Slice: T119 Global Admin Audit Log

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T111 introduced backend-owned global roles and T118 added the `admin-cli` grant/revoke operations tool. T119 makes those privileged role changes traceable by recording an audit entry for each mutating admin role command.

## Implementation Plan

Major topics:

1. Audit model
   - Add action and result enums for global role changes.
   - Add an immutable audit entry record.

2. Store contract
   - Extend `AuthStore` with append and list audit-log methods.
   - Implement in-memory storage for local and test profiles.
   - Implement JDBC persistence for Postgres.

3. CLI integration
   - Record grant and revoke command outcomes.
   - Support an optional `discord.admin-role.actor`.
   - Keep list commands read-only and unaudited.

4. Verification
   - Add RED/GREEN coverage for CLI audit recording.
   - Add Postgres store coverage for persisted audit entries.

## Out of Scope

- Public audit log API.
- Audit log dashboard UI.
- Retention and export policy.

## Acceptance Criteria

- Grant commands append a `GRANT/APPLIED` audit entry.
- Revoke commands append `REVOKE/APPLIED` or `REVOKE/NOOP`.
- Audit entries persist in Postgres.
- Unknown-user and missing-confirmation failures do not create audit entries.
- Focused backend tests pass.
