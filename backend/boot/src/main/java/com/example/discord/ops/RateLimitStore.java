package com.example.discord.ops;

import java.time.Instant;

interface RateLimitStore {
    RateLimitDecision consume(RateLimitKey key, RateLimitPolicy policy, Instant now);
}
