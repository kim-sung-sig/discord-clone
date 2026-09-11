---
slug: T145-remove-runtime-in-memory-persistence-defaults
ticket: T145
phase: plan
hub: "[[T145-remove-runtime-in-memory-persistence-defaults]]"
---
> 🧭 [[T145-remove-runtime-in-memory-persistence-defaults]] · PDCA: **plan** → [[T145-remove-runtime-in-memory-persistence-defaults.design]] → [[T145-remove-runtime-in-memory-persistence-defaults.analysis]] → [[T145-remove-runtime-in-memory-persistence-defaults.report]] → [[T145-remove-runtime-in-memory-persistence-defaults.feedback]]

# T145 Remove Runtime In-Memory Persistence Defaults Plan

Date: 2026-05-20
Slice: T145 Remove Runtime In-Memory Persistence Defaults

## Objective

Prevent production-like backend runs from silently using in-memory persistence stores when central Postgres is not enabled.

## Current State

- T144 aligned local central resources around Postgres, Redis, and Kafka.
- Auth, guild, message, invite, and global admin audit data still have in-memory implementations for local/test use.
- `production` or `admin-cli` without `postgres` could accidentally start against in-memory persistence.

## Scope

1. Add a runtime profile guard for production-like profiles.
2. Require `postgres` whenever `production` or `admin-cli` is active.
3. Exclude in-memory auth/guild/message/invite persistence beans from `production` and `admin-cli`.
4. Keep local default and `test` profile in-memory behavior available.

## Acceptance Criteria

- `production` without `postgres` fails with a clear error.
- `admin-cli` without `postgres` fails with a clear error.
- `production,postgres` is allowed.
- `test` remains allowed for in-memory test execution.
- Focused guard test, backend tests, and diff checks pass.
