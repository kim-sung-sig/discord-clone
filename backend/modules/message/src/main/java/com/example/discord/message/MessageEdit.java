package com.example.discord.message;

import java.time.Instant;

public record MessageEdit(String content, Instant editedAt) {
}
