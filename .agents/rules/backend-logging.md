---
id: backend-logging
description: Backend logging level and sensitive-data rules.
appliesTo:
  - backend
globs:
  - backend/**
triggers:
  - logging
  - logs
  - log level
  - observability
  - backend implementation
---
# Backend Logging

- Log application boundary method entry and completion at `info` when the operation is meaningful. Include safe parameters and safe result identifiers when useful.
- Do not log message bodies, tokens, cookies, passwords, authorization headers, shared secrets, or other sensitive values. Omit parameter/result logging when it cannot be safely summarized.
- Log database insert, update, and delete effects at `info` with entity type, safe identifiers, operation, and row count or status.
- Use `debug` for intermediate diagnostic values.
- Use `warn` for rejected, degraded, retried, or recoverable failure paths.
