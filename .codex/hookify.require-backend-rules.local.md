---
name: require-backend-rules
enabled: true
event: file
action: warn
conditions:
  - field: file_path
    operator: regex_match
    pattern: (^|[\\/])backend[\\/].*\.(java|kt|kts|sql|ya?ml|properties)$
---

Backend file edit detected.

Before continuing, load the matching nested project rules:

- `.agents/rules/README.md`
- `.agents/rules/backend/logging.md` for logging or observability changes

Keep detailed backend rules in `.agents/rules/backend/` instead of expanding `AGENTS.md`.
