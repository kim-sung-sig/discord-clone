# T19 Redis Rate Limit Store Design

작성일: 2026-05-14  
PDCA Phase: Design  
Slice: T19 Deployment Security/Abuse Controls

## Goal

Keep local tests deterministic while preventing the production architecture from being hard-wired to process memory.

## Store Contract

`RateLimitStore` has one atomic operation:

```text
consume(key, policy, now) -> decision
```

The operation increments the current fixed-window counter, applies an expiry for first use, and returns whether the request is allowed.

## Redis Key

```text
discord:rate-limit:{policy}:{subject}:{windowEpochMillis}
```

`subject` is already sanitized by the caller:

- `ip:{hash}`
- `token:{sha256}`

## Redis Algorithm

Preferred Lua script:

```lua
local count = redis.call("INCR", KEYS[1])
if count == 1 then
  redis.call("PEXPIRE", KEYS[1], ARGV[1])
end
return count
```

Caller computes:

- `allowed = count <= limit`
- `remaining = max(limit - count, 0)`
- `retryAfter = windowEnd - now`

## Operational Notes

- Redis timeout must fail closed for auth/gateway and fail open only for low-risk read endpoints. T19 rate-limited endpoints are write/auth paths, so production should fail closed.
- Metrics should count `rate_limit.allowed` and `rate_limit.rejected` by policy only, not subject.
- Raw bearer tokens, invite codes, and message bodies must never be used in Redis keys.
