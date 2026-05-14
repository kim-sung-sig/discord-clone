package com.example.discord.ops;

import java.time.Duration;

record RateLimitPolicy(String id, int limit, Duration window) {
}
