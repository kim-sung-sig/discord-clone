# T33 Production Secret & Config Baseline Design

## Goal
Production startup must fail loudly when required secrets or profile gates are missing, while local and CI fixtures stay explicit and non-production.

## Runtime Contract
| Setting | Local/CI | Production |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `postgres` for persistent QA, default in-memory for unit slices | must include `production,postgres` |
| `discord.auth.access-token-secret` | explicit local fixture allowed | required, at least 32 chars, no default/test value |
| `discord.gateway.internal-publisher-token` | explicit test harness token allowed in tests only | required, at least 32 chars, no `test-harness` |
| `spring.datasource.url` | local postgres URL allowed | required, not the local fallback URL |
| `spring.datasource.username` | `dev_user` allowed | required, not `dev_user` |
| `spring.datasource.password` | `dev_password` allowed | required, at least 32 chars, not `dev_password` |

## Secret Redaction
- Logs and QA artifacts must not print raw password, token, secret, API key, or bearer values.
- Redaction replaces sensitive values with `<redacted>`.
- Metadata may include non-sensitive routing information such as host, profile name, and artifact path.

## Rotation Guide
1. Generate new auth and gateway secrets with at least 32 random characters.
2. Deploy new secret values to the secret manager under versioned names.
3. Roll one backend instance with both old and new values when dual verification exists; until then, use a maintenance window because access tokens are signed with one active secret.
4. Rotate database passwords by creating a new DB credential, updating application secret references, rolling backend instances, then revoking the old credential.
5. LiveKit keys remain reserved until T41. When enabled, rotate API key/secret through provider console, deploy new values, verify token issue, then revoke old keys.

## QA
- Unit tests verify production profile failure for missing profile/default secrets and success for explicit production config.
- Unit tests verify secret redaction.
- CI/local docs keep fixture secrets visibly scoped to development.
