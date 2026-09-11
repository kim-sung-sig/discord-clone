---
slug: T118-global-admin-grant-operations-tool
ticket: T118
phase: plan
hub: "[[T118-global-admin-grant-operations-tool]]"
---
> 🧭 [[T118-global-admin-grant-operations-tool]] · PDCA: **plan** → [[T118-global-admin-grant-operations-tool.design]] → [[T118-global-admin-grant-operations-tool.analysis]] → [[T118-global-admin-grant-operations-tool.report]] → [[T118-global-admin-grant-operations-tool.feedback]]

# T118 Global Admin Grant Operations Tool Plan

## Objective

Provide an operational CLI path to grant, revoke, and list backend global roles such as `SECURITY_ADMIN` without exposing a new admin HTTP API.

## Current State

- T111 added `user_global_roles` and backend-owned `SECURITY_ADMIN`.
- There is no production-safe workflow for granting or revoking the role.
- Direct database edits are possible but error-prone and not audit-friendly.

## Scope

1. Add store support for revoking global roles.
2. Add a small admin role operation service.
3. Add an `admin-cli` Spring profile that runs without a web server.
4. Add a command runner for `grant`, `revoke`, and `list`.
5. Require explicit confirmation for mutating commands.
6. Document command properties.

## Acceptance Criteria

- `grant` adds `SECURITY_ADMIN` for an existing user.
- `revoke` removes `SECURITY_ADMIN`.
- `list` returns deterministic roles for a user.
- Mutating commands fail without `discord.admin-role.confirm=true`.
- Unknown users fail before mutation.
- Focused and full backend tests pass.

