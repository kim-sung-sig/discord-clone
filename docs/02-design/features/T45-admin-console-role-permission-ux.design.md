# T45 Admin Console & Role Permission UX Design

Date: 2026-05-18
Slice: T45 Admin Console & Role Permission UX

## Loop Output

계획 검토 됨 > 구현 계획 수립 됨 (주요토픽 안내, 주요 변경 사항 안내) > 구현 진행 함 > 리뷰 진행 > 리뷰 검토 (6개의 지표를 통해 점수를 매김) > 기준점 통과 시 다음 계획 진행, 미통과 시 개선안을 정리하여 구현 사이클 반복

## Architecture

T45 uses the existing Nuxt shell store and `RolePermissionPanel.vue`.

- Store owns the admin console state: selected role, staged permission diff, and preview target.
- Component renders the admin console sections.
- Mutation action updates the role and uses the existing `appendAuditLog` path for privileged audit visibility.

This avoids adding backend REST surface before the UX contract is clear.

## Data Model

The web shell store gains:

- `ShellAdminPermissionDraft`: role id, permission, before state, after state.
- `ShellAdminConsoleState`: preview role id and optional draft.

The draft is intentionally small. It represents one pending privileged mutation and can later map to an API request body.

## Permission Preview

Preview-as-role calculates the active channel result using the same high-level order as the backend permission calculator:

1. Start with `@everyone` permissions.
2. Grant selected role permissions.
3. Apply active channel overwrite denies.
4. Apply active channel overwrite allows.

The panel displays allowed and denied permissions separately so channel overwrite effects are visible.

## Mutation Flow

The panel starts with a staged Moderator `MANAGE_CHANNELS` grant. When the admin applies it:

1. Store updates the role permission set.
2. Store appends `ROLE_PERMISSION_UPDATED` to moderation audit logs.
3. The visible audit section updates immediately.

## Testing

Component tests assert:

- Diff preview is rendered.
- Preview-as-role includes overwrite-derived allow and deny outcomes.
- Applying the staged change writes visible audit and updates the role card.
