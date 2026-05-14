package com.example.discord.ops;

import java.time.Duration;

record RateLimitDecision(boolean allowed, int limit, int remaining, Duration retryAfter) {
}
