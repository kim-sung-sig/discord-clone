# T02B Role/Permission Management Design

작성일: 2026-05-13  
PDCA Phase: Design  
Parent Phase: T02 Guild/Channel/Permission  
Slice: T02-B role/member/overwrite hardening

## Backend Design

### Domain

- Keep `InMemoryGuildService` as the slice storage boundary.
- Add `roles(UUID guildId)` for role listing.
- Reuse existing `createRole`, `assignRolePermissions`, `assignRoleToMember`, and `addChannelRoleOverwrite`.
- Add validation in service/controller for null identifiers and null permission lists.
- Parse permissions from request strings into `PermissionSet`.

### REST API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/guilds/{guildId}/roles` | List guild roles including `@everyone`. |
| `POST` | `/api/guilds/{guildId}/roles` | Create role with optional permissions. |
| `PUT` | `/api/guilds/{guildId}/roles/{roleId}/permissions` | Replace role permissions. |
| `PUT` | `/api/guilds/{guildId}/members/{memberId}/roles/{roleId}` | Assign role to member idempotently. |
| `PUT` | `/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}` | Replace channel role overwrite allow/deny. |

### Response DTOs

- `RoleResponse(id, name, permissions)`.
- `MemberRoleResponse(memberId, roleIds)`.
- `ChannelResponse(id, name, type, parentId)` remains unchanged for visible-channel tests.

## Frontend Design

### Store

- Add `ShellRole`, `ShellPermissionOverwrite`, and `ShellMember`.
- Represent role permissions and channel overwrites explicitly.
- Add getters for role summary and active channel overwrites.

### UI

- Add `RolePermissionPanel.vue` to render:
  - role list with permissions
  - member role assignment summary
  - active channel overwrite summary
- Mount panel in `app.vue` or an existing shell layout area without changing routing.

## Test Design

- Backend RED test: full API sequence creates role, grants permission, assigns role, denies `VIEW_CHANNEL` overwrite, verifies channel disappears from visible channels.
- Backend RED test: null permission payload returns 400.
- Frontend RED test: management panel renders role, member role, and overwrite state.
- E2E RED test: app shell shows the role management panel.

