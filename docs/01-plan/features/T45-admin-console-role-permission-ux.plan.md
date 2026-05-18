# T45 Admin Console & Role Permission UX Plan

Date: 2026-05-18
Slice: T45 Admin Console & Role Permission UX

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 > 기준점 통과 여부 판단 > 다음 계획 또는 개선 반복

## Plan Review

T45 focuses on making role and channel permission changes understandable before mutation. The existing web shell already renders role permissions, member role assignments, and active channel overwrites. This slice upgrades that surface into a lightweight admin console preview.

## Implementation Plan

Major topics:

1. Role permission diff
   - Show a staged role permission change before applying it.
   - Display before/after state and the affected role/permission.

2. Preview-as-role
   - Show effective permissions for a selected role on the active channel.
   - Include channel overwrite allow/deny effects so the preview matches backend authorization semantics.

3. Privileged action audit visibility
   - Applying a staged permission change appends an audit entry.
   - The admin panel shows the latest privileged audit activity.

## Out of Scope

- Production REST mutation endpoint.
- Full role ordering drag-and-drop.
- Full channel overwrite editor with arbitrary allow/deny editing.
- Backend hierarchy enforcement beyond already existing domain modules.

## Acceptance Criteria

- Admin panel renders a pending role permission diff with before/after state.
- Preview-as-role renders effective allow and deny outcomes for the active channel.
- Applying a privileged change updates role permissions and appends an audit entry.
- Existing role/overwrite panel behavior remains visible.

## Failure Criteria

- UI suggests permission is allowed while the preview denies it.
- Permission change can be applied without visible audit feedback.
- Existing role/member/overwrite display regresses.
